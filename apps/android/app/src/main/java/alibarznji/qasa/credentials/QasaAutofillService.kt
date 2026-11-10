package alibarznji.qasa.credentials

import alibarznji.qasa.MainActivity
import alibarznji.qasa.R
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.vault.VaultPayload
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
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
import androidx.autofill.inline.v1.InlineSuggestionUi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The Android autofill service (docs/autofill-plan.md §3.3).
 *
 * Suggestions come from the plain part of the autofill index (title, masked hint), so they
 * show while the vault is locked. Every dataset is authenticated: no value leaves the vault
 * until the user picks a suggestion and [AutofillAuthActivity] has unlocked or confirmed.
 * Logins are offered only to the site or app they belong to ([AutofillMatcher]); cards and
 * profiles to any form that asks for them, behind the same unlock.
 */
@AndroidEntryPoint
class QasaAutofillService : AutofillService() {
    @Inject lateinit var indexStore: AutofillIndexStore
    @Inject lateinit var appSession: AppSession

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val screen = AutofillStructureParser.parse(structure)
        // Never fill into Qasa itself.
        if ((screen.caller as? AutofillCaller.App)?.packageName == packageName) return callback.onSuccess(null)
        val form = screen.form
        if (!form.hasLogin && !form.hasCard && !form.hasIdentity) return callback.onSuccess(null)

        val entries = indexStore.read()
        val inline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) InlineSpecs(request) else null
        val response = FillResponse.Builder()
        var added = 0

        fun offer(group: AutofillGroup, candidates: List<AutofillIndexEntry>, limit: Int) {
            val ids = form.group(group).map { it.id }
            if (ids.isEmpty()) return
            for (entry in candidates.take(limit)) {
                response.addDataset(dataset(ids, group, entry.id, entry.title, AutofillPresentation.hint(entry), inline))
                added++
            }
            // Always last: search the whole vault (and unlock first when locked).
            response.addDataset(dataset(ids, group, null, getString(R.string.autofill_searchqasa), getString(R.string.autofill_title), inline))
            added++
        }

        if (form.hasLogin) {
            val logins = entries.filter { it.kind == AutofillIndexStore.KIND_LOGIN }
            offer(AutofillGroup.Login, AutofillMatcher.rank(logins, screen.caller) { it.website ?: it.websiteHost }, 5)
        }
        if (form.hasCard) offer(AutofillGroup.Card, entries.filter { it.kind == AutofillIndexStore.KIND_CARD }, 5)
        if (form.hasIdentity) {
            val profiles = entries.filter { it.kind in AutofillIndexStore.IDENTITY_KINDS }
                .sortedBy { AutofillIndexStore.IDENTITY_KINDS.indexOf(it.kind) }
            offer(AutofillGroup.Identity, profiles, 5)
        }
        if (added == 0) return callback.onSuccess(null)

        // Offer to save a login typed into this form.
        form.passwordField?.let { password ->
            val username = form.group(AutofillGroup.Login).firstOrNull { it.type == AutofillFieldType.Username }
            val type = SaveInfo.SAVE_DATA_TYPE_PASSWORD or (if (username != null) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0)
            val save = SaveInfo.Builder(type, arrayOf(password.id))
            username?.let { save.setOptionalIds(arrayOf(it.id)) }
            response.setSaveInfo(save.build())
        }
        try {
            callback.onSuccess(response.build())
        } catch (_: RuntimeException) {
            callback.onSuccess(null)
        }
    }

    /**
     * A login was typed and submitted: open the app's new-login form with it, so the user
     * reviews and saves it there. Nothing is saved without that step.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess()
        val screen = AutofillStructureParser.parse(structure)
        val password = screen.form.passwordField?.let { screen.textValues[it.id] }?.takeIf { it.isNotEmpty() }
            ?: return callback.onSuccess()
        val username = screen.form.group(AutofillGroup.Login)
            .firstOrNull { it.type == AutofillFieldType.Username }
            ?.let { screen.textValues[it.id] }.orEmpty()
        val (name, website) = when (val caller = screen.caller) {
            is AutofillCaller.Web -> caller.host to caller.host
            is AutofillCaller.App -> appLabel(caller.packageName) to "androidapp://${caller.packageName}"
        }
        appSession.requestSaveLogin(
            VaultPayload.Login(itemName = name, username = username, password = password, website = website),
        )
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) callback.onSuccess(pending.intentSender) else callback.onSuccess()
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

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
