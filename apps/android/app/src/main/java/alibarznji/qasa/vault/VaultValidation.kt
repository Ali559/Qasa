package alibarznji.qasa.vault

import alibarznji.qasa.R
import android.content.res.Resources
import java.time.LocalDate
import uniffi.qasa_ffi.FfiDate
import uniffi.qasa_ffi.FfiField
import uniffi.qasa_ffi.FfiFieldIssue
import uniffi.qasa_ffi.FfiProblem

/** A user save refused because the item still breaks a field rule; nothing was written. */
class VaultValidationException(val issues: List<FfiFieldIssue>) :
    IllegalArgumentException("Invalid fields: " + issues.joinToString { it.code })

/**
 * The shared field rules (`core/qasa-vault/src/validate.rs`, through `qasa-ffi`) applied to
 * Android's payload model. Payloads cross the FFI in Android's own JSON shape, encoded with
 * the repository's serializer ([VaultJson]); `normalizePayload` answers in the same shape.
 */
object VaultValidation {
    /** The device's local day; swappable so tests can pin it. */
    @Volatile
    var today: () -> LocalDate = { LocalDate.now() }

    /** The stored canonical form of what was typed (ISO dates, `MM/YY`, grouped card numbers…). */
    fun normalize(payload: VaultPayload): VaultPayload =
        VaultJson.decodePayload(uniffi.qasa_ffi.normalizePayload(VaultJson.encodePayload(payload)))

    /** [normalize], or [payload] unchanged when the core can't read it. */
    fun normalizeOrSelf(payload: VaultPayload): VaultPayload = runCatching { normalize(payload) }.getOrDefault(payload)

    /** Every issue with [payload] as it is, at most one per field, in form order. */
    fun validate(payload: VaultPayload, today: LocalDate = this.today()): List<FfiFieldIssue> =
        uniffi.qasa_ffi.validatePayload(VaultJson.encodePayload(payload), today.toFfi())

    /** What a save would refuse: the issues left once [payload] is normalized. */
    fun issuesAfterNormalize(payload: VaultPayload, today: LocalDate = this.today()): List<FfiFieldIssue> =
        validate(normalize(payload), today)

    /**
     * The repository's write policy for [origin] (ARCHITECTURE.md §5): what to store, and
     * the issues it still has.
     * - [SaveOrigin.User]: normalized, then validated; any issue throws
     *   [VaultValidationException] so nothing is written.
     * - [SaveOrigin.Import]: normalized and stored even with issues (returned, for the count).
     * - [SaveOrigin.Internal]: stored exactly as given.
     */
    fun prepareForWrite(
        input: VaultPayload,
        origin: SaveOrigin,
        today: LocalDate = this.today(),
    ): Pair<VaultPayload, List<FfiFieldIssue>> = when (origin) {
        SaveOrigin.User -> {
            val normalized = normalize(input)
            val issues = validate(normalized, today)
            if (issues.isNotEmpty()) throw VaultValidationException(issues)
            normalized to issues
        }
        SaveOrigin.Import -> {
            val normalized = normalizeOrSelf(input)
            normalized to runCatching { validate(normalized, today) }.getOrDefault(emptyList())
        }
        SaveOrigin.Internal -> input to emptyList()
    }

    /** A stored date in any form Qasa reads (ISO, legacy "Nov 6, 2000", Arabic-Indic digits…). */
    fun parseDate(raw: String): LocalDate? =
        uniffi.qasa_ffi.parseVaultDate(raw)?.let { LocalDate.of(it.year, it.month.toInt(), it.day.toInt()) }

    fun LocalDate.toFfi() = FfiDate(year = year, month = monthValue.toUInt(), day = dayOfMonth.toUInt())

    /** One message per problem; the strings come from `l10n/strings.json` (`validation.*`). */
    fun message(problem: FfiProblem, res: Resources): String = when (problem) {
        FfiProblem.Required -> res.getString(R.string.validation_required)
        is FfiProblem.TooLong -> res.getString(R.string.validation_toolong, problem.max.toInt())
        FfiProblem.DigitsOnly -> res.getString(R.string.validation_digitsonly)
        FfiProblem.LettersDigitsOnly -> res.getString(R.string.validation_lettersdigitsonly)
        FfiProblem.CardNumberLength -> res.getString(R.string.validation_cardnumberlength)
        FfiProblem.CardNumberChecksum -> res.getString(R.string.validation_cardnumberchecksum)
        is FfiProblem.SecurityCodeLength ->
            if (problem.min == problem.max) {
                res.getString(R.string.validation_securitycodeexact, problem.min.toInt())
            } else {
                res.getString(R.string.validation_securitycoderange, problem.min.toInt(), problem.max.toInt())
            }
        FfiProblem.ExpiryFormat -> res.getString(R.string.validation_expiryformat)
        FfiProblem.ExpiryMonth -> res.getString(R.string.validation_expirymonth)
        is FfiProblem.ExpiryTooFar -> res.getString(R.string.validation_yearsaway, problem.maxYears.toInt())
        FfiProblem.DateInvalid -> res.getString(R.string.validation_dateinvalid)
        FfiProblem.DateInFuture -> res.getString(R.string.validation_dateinfuture)
        FfiProblem.DateTooOld -> res.getString(R.string.validation_datetooold)
        FfiProblem.DateBeforeIssue -> res.getString(R.string.validation_datebeforeissue)
        is FfiProblem.DateTooFar -> res.getString(R.string.validation_yearsaway, problem.maxYears.toInt())
        FfiProblem.EmailInvalid -> res.getString(R.string.validation_emailinvalid)
        FfiProblem.UrlInvalid -> res.getString(R.string.validation_urlinvalid)
        is FfiProblem.PhoneLength -> res.getString(R.string.validation_phonelength, problem.min.toInt(), problem.max.toInt())
        FfiProblem.PhoneCountryMismatch -> res.getString(R.string.validation_phonecountrymismatch)
        FfiProblem.IbanInvalid -> res.getString(R.string.validation_ibaninvalid)
        FfiProblem.IbanChecksum -> res.getString(R.string.validation_ibanchecksum)
        FfiProblem.RoutingInvalid -> res.getString(R.string.validation_routinginvalid)
        FfiProblem.CountryInvalid -> res.getString(R.string.validation_countryinvalid)
        FfiProblem.TotpInvalid -> res.getString(R.string.validation_totpinvalid)
    }

    /** Issues keyed by field (the core reports at most one per field). */
    fun byField(issues: List<FfiFieldIssue>): Map<FfiField, FfiProblem> = issues.associate { it.field to it.problem }
}
