package alibarznji.qasa.credentials

import alibarznji.qasa.MainActivity
import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.prefs.LocaleContext
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.services.AutoLockMonitor
import alibarznji.qasa.vault.VaultSession
import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.crypto.PasswordGenerator
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillEventHistory
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.core.os.BundleCompat
import androidx.autofill.inline.v1.InlineSuggestionUi
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

/**
 * The Android autofill service (docs/autofill-plan.md §3.3).
 *
 * Suggestions come from the plain part of the autofill index (title, masked hint), so they
 * show while the vault is locked. Every dataset is authenticated: no value leaves the vault
 * until the user picks a suggestion and [AutofillAuthActivity] has unlocked or confirmed.
 * Logins are offered only to the site or app they belong to ([AutofillMatcher]); cards and
 * profiles to any form that asks for them, behind the same unlock.
 *
 * One exception (R8, docs/autofill-hardening.md): the login just picked for this app or site,
 * when its next request is for a field that pick didn't fill (an app that reports one field
 * at a time), comes first and carries its values, read from the unlocked vault, so it fills
 * without unlocking again. [RecentLoginPicks] holds only its id, for 2 minutes.
 */
@AndroidEntryPoint
class QasaAutofillService : AutofillService() {
    @Inject lateinit var indexStore: AutofillIndexStore
    @Inject lateinit var appSession: AppSession
    @Inject lateinit var generated: GeneratedPasswordStore
    @Inject lateinit var vaultSession: VaultSession
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var autoLock: AutoLockMonitor

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        // R4: a strong password picked on the last screen is recorded before anything else;
        // R8: so is a pick of "the login you just picked" (it has no authentication to see it).
        recordPicksFromHistory(emptyList(), null)
        val screen = AutofillStructureParser.parse(structure)
        // Never fill into Qasa itself.
        if ((screen.caller as? AutofillCaller.App)?.packageName == packageName) return callback.onSuccess(null)
        val form = screen.form
        if (!form.hasLogin && !form.hasNewPassword && !form.hasCard && !form.hasIdentity) return callback.onSuccess(null)

        val entries = indexStore.read()
        // A service's resources follow the device language; the dataset labels follow the app's.
        val l10n = LocaleContext.forApp(this)
        val inline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) InlineSpecs(request) else null
        val response = FillResponse.Builder()
        var added = 0
        val loginOffers = AutofillOffers.login(form, screen.focused)
        val now = System.currentTimeMillis()

        /**
         * "Use a strong password" on [ids]: a fresh password from the shared generator, fitted
         * to those fields (R2), recorded as offered (R4). [fallback] is R7's row on a login
         * password: it fills only that field.
         */
        fun addStrongPassword(ids: List<AutofillId>, fallback: Boolean) {
            val signals = form.fields.filter { it.id in ids }.map { it.signals }
            val password = runCatching { PasswordGenerator.generate(AutofillOffers.strongPasswordSpec(signals)) }
                .getOrElse { PasswordGenerator.generateDefault() }
            val datasetId = GeneratedPasswordLog.DATASET_PREFIX + UUID.randomUUID()
            // R4: kept (encrypted, briefly) so a pick reported later by id still has its value.
            val (name, website) = loginTarget(screen.caller)
            generated.update { GeneratedPasswordLog.offered(it, GeneratedPassword(datasetId, name, website, password, now), now) }
            val targets = if (fallback) ids else emptyList()
            response.addDataset(strongPasswordDataset(ids, targets, datasetId, password, l10n.getString(R.string.fill_strongpassword), inline))
            added++
        }

        fun offer(group: AutofillGroup, candidates: List<AutofillIndexEntry>, limit: Int) {
            val ids = form.group(group).map { it.id }
            if (ids.isEmpty()) return
            for (entry in candidates.take(limit)) {
                response.addDataset(dataset(ids, group, entry.id, entry.title, AutofillPresentation.hint(entry), inline))
                added++
            }
            // Always last: search the whole vault (and unlock first when locked).
            response.addDataset(dataset(ids, group, null, l10n.getString(R.string.autofill_searchqasa), l10n.getString(R.string.autofill_title), inline))
            added++
        }

        // Logins (R1, R7, R8): a new-password field gets one suggestion, a fresh password and
        // never a saved login (design rule 6c), first so it gets a keyboard (inline) slot. A
        // login password R1 couldn't call new gets the strong password after its matches.
        val logins = entries.filter { it.kind == AutofillIndexStore.KIND_LOGIN }
        val matches = if (form.hasLogin) AutofillMatcher.rank(logins, screen.caller) { it.website ?: it.websiteHost } else emptyList()
        val focusedType = form.fields.firstOrNull { it.id == screen.focused }?.type
        val recent = RecentLoginPicks.cache.lookup(screen.caller, focusedType, now)?.let { id -> logins.firstOrNull { it.id == id } }
        for (row in AutofillOffers.loginRows(loginOffers, matches, recent)) {
            when (row) {
                is LoginRow.StrongPassword ->
                    addStrongPassword(if (row.fallback) loginOffers.fallbackStrongIds else loginOffers.strongPasswordIds, row.fallback)
                is LoginRow.Saved -> {
                    val ids = loginOffers.savedLoginIds
                    val direct = if (row.recent) recentDataset(screen, row.entry, inline) else null
                    response.addDataset(direct ?: dataset(ids, AutofillGroup.Login, row.entry.id, row.entry.title, AutofillPresentation.hint(row.entry), inline))
                    added++
                }
                LoginRow.Search -> {
                    response.addDataset(
                        dataset(loginOffers.savedLoginIds, AutofillGroup.Login, null, l10n.getString(R.string.autofill_searchqasa), l10n.getString(R.string.autofill_title), inline),
                    )
                    added++
                }
            }
        }
        if (form.hasCard) offer(AutofillGroup.Card, entries.filter { it.kind == AutofillIndexStore.KIND_CARD }, 5)
        if (form.hasIdentity) {
            val profiles = entries.filter { it.kind in AutofillIndexStore.IDENTITY_KINDS }
                .sortedBy { AutofillIndexStore.IDENTITY_KINDS.indexOf(it.kind) }
            offer(AutofillGroup.Identity, profiles, 5)
        }
        if (added == 0) return callback.onSuccess(null)

        // Offer to save a login typed into this form, or one whose password we just generated.
        //
        // The framework (Session#showSaveLocked) skips the save UI in two cases that matter
        // here, and no SaveInfo flag turns either off:
        //  1. "no value changed": every watched field still holds what a dataset filled, and
        //     no optional field was edited by the user;
        //  2. "dataset match": every watched field's value equals one dataset's value.
        // On a sign-up form the user types the username (an optional id the strong-password
        // dataset never carries), so neither applies and Android offers to save. On a change
        // form the current password is watched too (AutofillOffers.save), so typing it does the
        // same. Left out: a form whose only watched fields Qasa filled itself (a reset page
        // with no username, or a change form whose current password came from a saved login);
        // Android shows no save prompt there.
        //
        // R4 two-screen (Android 12+, apps only): a username screen delays its save and keeps
        // the username's id in the client state; the password screen, same app, reads it back
        // and requires both, so the save request carries both screens.
        val twoScreen = TwoScreenSave.applies(screen.caller, Build.VERSION.SDK_INT, isCompatMode(request))
        val previousUsername = if (twoScreen) delayedUsername(request.clientState, screen.caller) else null
        val plan = if (twoScreen) TwoScreenSave.plan(form, previousUsername) else AutofillOffers.save(form)
        if (plan != null) {
            val fromEarlierScreen = plan.usernameId != null && plan.usernameId == previousUsername
            val type = SaveInfo.SAVE_DATA_TYPE_PASSWORD or (if (plan.usernameId != null) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0)
            val required = if (fromEarlierScreen) arrayOf(plan.usernameId!!, plan.passwordId) else arrayOf(plan.passwordId)
            val save = SaveInfo.Builder(type, required)
            val optional = plan.optionalIds.filter { it !in required }
            if (optional.isNotEmpty()) save.setOptionalIds(optional.toTypedArray())
            response.setSaveInfo(save.build())
        } else if (twoScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TwoScreenSave.delayedOf(form)?.let { delayed ->
                val type = if (delayed.isEmail) SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS else SaveInfo.SAVE_DATA_TYPE_USERNAME
                response.setSaveInfo(SaveInfo.Builder(type, arrayOf(delayed.usernameId)).setFlags(SaveInfo.FLAG_DELAY_SAVE).build())
                val app = screen.caller as AutofillCaller.App
                response.setClientState(
                    Bundle().apply {
                        putParcelable(TwoScreenSave.KEY_USERNAME_ID, delayed.usernameId)
                        putString(TwoScreenSave.KEY_PACKAGE, app.packageName)
                    },
                )
            }
        }
        try {
            callback.onSuccess(response.build())
        } catch (_: RuntimeException) {
            callback.onSuccess(null)
        }
    }

    private fun isCompatMode(request: FillRequest): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && request.flags and FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST != 0

    /** R4 two-screen: the earlier screen's username id, when the client state is this app's. */
    private fun delayedUsername(state: Bundle?, caller: AutofillCaller): AutofillId? {
        val app = caller as? AutofillCaller.App ?: return null
        if (state?.getString(TwoScreenSave.KEY_PACKAGE) != app.packageName) return null
        return BundleCompat.getParcelable(state, TwoScreenSave.KEY_USERNAME_ID, AutofillId::class.java)
    }

    /**
     * R8: "the login you just picked", filled without its authentication: only while the
     * vault is unlocked and no confirmation is due (AutofillPolicy). The values are read
     * from the vault here, never from the cache. Null: use the normal authenticated row.
     */
    @SuppressLint("NewApi")
    private fun recentDataset(screen: ParsedScreen, entry: AutofillIndexEntry, inline: InlineSpecs?): Dataset? {
        autoLock.lockIfExpiredInBackground()
        if (AutofillPolicy.needsAuth(entry.kind, vaultSession.isUnlocked, prefs.autofillConfirmEveryFill.value)) return null
        val values = AutofillDatasets.values(this, indexStore, screen, AutofillGroup.Login, entry, copyTotp = false) ?: return null
        val filled = screen.form.fields.filter { it.id in values.keys }.map { it.type }.toSet()
        val hint = AutofillPresentation.hint(entry)
        val presentation = RemoteViews(packageName, R.layout.autofill_suggestion).apply {
            setTextViewText(R.id.autofill_title, entry.title)
            setTextViewText(R.id.autofill_hint, hint)
        }
        val inlinePresentation = inline?.next(this, entry.title, hint, AutofillGroup.Login, entry.id)
        @Suppress("DEPRECATION")
        val builder = Dataset.Builder(presentation)
        builder.setId(RecentLoginPicks.datasetId(entry.id, filled))
        for ((id, value) in values) {
            val autofillValue = AutofillDatasets.autofillValue(value)
            @Suppress("DEPRECATION")
            if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setValue(id, autofillValue, presentation, inlinePresentation)
            } else {
                builder.setValue(id, autofillValue, presentation)
            }
        }
        return builder.build()
    }

    /**
     * A login was typed and submitted: open the app's new-login form with it, so the user
     * reviews and saves it there. Nothing is saved without that step.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val contexts = request.fillContexts
        val structure = contexts.lastOrNull()?.structure ?: return callback.onSuccess()
        val screen = AutofillStructureParser.parse(structure)
        // The new password on a sign-up or change form (AutofillForm.passwordField).
        val password = screen.form.passwordField?.let { screen.textValues[it.id] }?.takeIf { it.isNotEmpty() }
            ?: return callback.onSuccess()
        val current = AutofillOffers.save(screen.form)?.usernameId?.let { screen.textValues[it] }
        // R4 two-screen: the username was typed on an earlier screen of the same app.
        val earlier = contexts.dropLast(1).map { AutofillStructureParser.parse(it.structure) }
            .filter { it.caller == screen.caller }
        val username = TwoScreenSave.username(
            current = current,
            earlierValues = earlier.map { it.textValues },
            previousUsername = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) delayedUsername(request.clientState, screen.caller) else null,
            earlierUsernames = earlier.map { s -> TwoScreenSave.delayedOf(s.form)?.let { s.textValues[it.usernameId] } },
        )
        // R4: the generated password is being saved here; still recorded as picked, so the
        // unlock reminder catches it if the user closes the review without saving.
        val picked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) request.datasetIds.orEmpty() else emptyList()
        recordPicksFromHistory(picked, password)
        val (name, website) = loginTarget(screen.caller)
        appSession.requestSaveLogin(
            VaultPayload.Login(itemName = name, username = username, password = password, website = website),
        )
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) callback.onSuccess(pending.intentSender) else callback.onSuccess()
    }

    private fun loginTarget(caller: AutofillCaller): Pair<String, String> = when (caller) {
        is AutofillCaller.Web -> caller.host to caller.host
        is AutofillCaller.App -> appLabel(caller.packageName) to "androidapp://${caller.packageName}"
    }

    /**
     * R4: marks offered strong passwords the user picked. [StrongPasswordPickActivity] does
     * this at the pick; this is the fallback. The fill event history holds the events of
     * this service's last response (selected / authentication selected carry the dataset
     * id); a save request also lists the datasets picked in its session ([extraIds]), and a
     * saved [password] equal to an offered one is a pick too.
     *
     * R8: a pick of "the login you just picked" (no authentication, so only the history
     * reports it) adds the fields it filled to the cached pick.
     */
    private fun recordPicksFromHistory(extraIds: List<String>, password: String?) {
        val fromHistory = runCatching {
            fillEventHistory?.events.orEmpty()
                .filter {
                    it.type == FillEventHistory.Event.TYPE_DATASET_SELECTED ||
                        it.type == FillEventHistory.Event.TYPE_DATASET_AUTHENTICATION_SELECTED
                }
                .mapNotNull { it.datasetId }
        }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        fromHistory.mapNotNull(RecentLoginPicks::parseDatasetId).forEach { (item, filled) ->
            RecentLoginPicks.cache.recordAgain(item, filled, now)
        }
        val ids = (fromHistory + extraIds).filter { it.startsWith(GeneratedPasswordLog.DATASET_PREFIX) }.toSet()
        if (ids.isEmpty() && password == null) return
        generated.update { GeneratedPasswordLog.picked(it, ids, password) }
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    /**
     * "Use a strong password": the generated value as the row's subtitle, filled into every
     * new-password field (new and confirm). Its authentication is [StrongPasswordPickActivity],
     * which has no UI and needs no unlock (a fresh random password is no vault data): it
     * records the pick at once (R4) and hands the values back.
     */
    @SuppressLint("NewApi")
    private fun strongPasswordDataset(
        ids: List<AutofillId>,
        targets: List<AutofillId>,
        datasetId: String,
        password: String,
        title: String,
        inline: InlineSpecs?,
    ): Dataset {
        val presentation = RemoteViews(packageName, R.layout.autofill_suggestion).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_hint, password)
        }
        val inlinePresentation = inline?.next(this, title, password, AutofillGroup.Login, STRONG_PASSWORD_ID)
        @Suppress("DEPRECATION")
        val builder = Dataset.Builder(presentation)
        // The id also comes back in the fill event history (the fallback, R4).
        builder.setId(datasetId)
        for (id in ids) {
            @Suppress("DEPRECATION")
            if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setValue(id, null, presentation, inlinePresentation)
            } else {
                builder.setValue(id, null, presentation)
            }
        }
        builder.setAuthentication(StrongPasswordPickActivity.intentSender(this, datasetId, targets))
        return builder.build()
    }

    @SuppressLint("NewApi")
    private fun dataset(
        ids: List<AutofillId>,
        group: AutofillGroup,
        entryId: String?,
        title: String,
        hint: String,
        inline: InlineSpecs?,
    ): Dataset {
        val presentation = RemoteViews(packageName, R.layout.autofill_suggestion).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_hint, hint)
        }
        val inlinePresentation = inline?.next(this, title, hint, group, entryId)
        @Suppress("DEPRECATION") // The Presentations builder is API 33; this one works on 26+.
        val builder = Dataset.Builder(presentation)
        for (id in ids) {
            @Suppress("DEPRECATION")
            if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setValue(id, null, presentation, inlinePresentation)
            } else {
                builder.setValue(id, null, presentation)
            }
        }
        builder.setAuthentication(AutofillAuthActivity.intentSender(this, group, entryId))
        return builder.build()
    }
}

/** The inline attribution's request-code key for the strong-password row (no vault item). */
private const val STRONG_PASSWORD_ID = "strong-password"

/** Keyboard (inline) suggestions, when the keyboard asks for them (Android 11+). */
@RequiresApi(Build.VERSION_CODES.R)
private class InlineSpecs(request: FillRequest) {
    private val specs: List<InlinePresentationSpec> = request.inlineSuggestionsRequest?.inlinePresentationSpecs.orEmpty()
    private val max: Int = request.inlineSuggestionsRequest?.maxSuggestionCount ?: 0
    private var used = 0

    fun next(service: QasaAutofillService, title: String, hint: String, group: AutofillGroup, entryId: String?): InlinePresentation? {
        if (specs.isEmpty() || used >= max) return null
        val spec = specs[minOf(used, specs.size - 1)]
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
        used++
        val attribution = PendingIntent.getActivity(
            service,
            (group.name + (entryId ?: "") + "inline").hashCode(),
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val content = InlineSuggestionUi.newContentBuilder(attribution)
            .setTitle(title)
            .setStartIcon(Icon.createWithResource(service, R.mipmap.ic_launcher))
            .apply { if (hint.isNotBlank()) setSubtitle(hint) }
            .build()
        return InlinePresentation(content.slice, spec, false)
    }
}
