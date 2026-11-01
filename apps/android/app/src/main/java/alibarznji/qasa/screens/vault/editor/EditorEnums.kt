package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaColors
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/*
 * Ports of the picker enums in iOS `Models/VaultEnums.swift:165-236`.
 *
 * The payload stores `iosRaw` ("None of these", "Work Cell"), the iOS enum raw values, so
 * the fields decode on iOS (its Codable enums reject anything else). `raw` is the older
 * lower-camel spelling Android briefly used; `fromRaw` accepts both.
 */

enum class Honorific(val raw: String, val iosRaw: String, @StringRes val label: Int) {
    Mr("mr", "Mr", R.string.honorific_mr),
    Mrs("mrs", "Mrs", R.string.honorific_mrs),
    Ms("ms", "Ms", R.string.honorific_ms),
    Mx("mx", "Mx", R.string.honorific_mx),
    Miss("miss", "Miss", R.string.honorific_miss),
    None("none", "None of these", R.string.honorific_none);

    companion object {
        fun fromRaw(raw: String): Honorific =
            entries.firstOrNull { it.raw.equals(raw, true) || it.iosRaw == raw } ?: None
    }
}

enum class EmailKind(val raw: String, val iosRaw: String, @StringRes val label: Int) {
    Personal("personal", "Personal", R.string.emailkind_personal),
    Work("work", "Work", R.string.emailkind_work);

    companion object {
        fun fromRaw(raw: String): EmailKind =
            entries.firstOrNull { it.raw.equals(raw, true) || it.iosRaw == raw } ?: Personal
    }
}

enum class PhoneKind(val raw: String, val iosRaw: String, @StringRes val label: Int) {
    Cell("cell", "Cell", R.string.phonekind_cell),
    Work("work", "Work", R.string.phonekind_work),
    WorkCell("workCell", "Work Cell", R.string.phonekind_workcell),
    WorkFax("workFax", "Work Fax", R.string.phonekind_workfax),
    Fax("fax", "Fax", R.string.phonekind_fax),
    HomePhone("homePhone", "Home Phone", R.string.phonekind_homephone),
    Other("other", "Other", R.string.phonekind_other);

    companion object {
        fun fromRaw(raw: String): PhoneKind =
            entries.firstOrNull { it.raw.equals(raw, true) || it.iosRaw == raw } ?: Cell
    }
}

/** iOS `NoteColor` — shared by secure notes (default mint) and cards (default blue). */
enum class NoteColor(val raw: String, @StringRes val label: Int) {
    Mint("mint", R.string.color_mint),
    Gold("gold", R.string.color_gold),
    Blue("blue", R.string.color_blue),
    Purple("purple", R.string.color_purple),
    Red("red", R.string.color_red),
    Gray("gray", R.string.color_gray);

    /** `NoteColor.color` (VaultEnums.swift:226): blue/purple are literal hexes on iOS too. */
    fun color(c: QasaColors): Color = when (this) {
        Mint -> Color(0xFF2BB58A) // the note colour named mint, not the accent
        Gold -> c.gold
        Blue -> Color(0xFF3B82F6)
        Purple -> Color(0xFFA855F7)
        Red -> c.danger
        Gray -> c.textSecondary
    }

    companion object {
        fun fromRaw(raw: String, default: NoteColor = Mint): NoteColor =
            entries.firstOrNull { it.raw == raw } ?: default
    }
}
