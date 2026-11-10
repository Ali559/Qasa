package alibarznji.qasa.credentials

import android.app.assist.AssistStructure
import android.os.Build
import android.view.View
import android.view.autofill.AutofillId

/** A screen as the autofill framework reports it, classified. */
class ParsedScreen(
    val form: AutofillForm<AutofillId>,
    val caller: AutofillCaller,
    /** The field that asked; null when the request came without focus (a manual request). */
    val focused: AutofillId?,
    /** The typed values, for a save request. */
    val textValues: Map<AutofillId, String>,
)

/** Walks an `AssistStructure` into [FieldSignals] and classifies them (plan §3.3). */
object AutofillStructureParser {
    fun parse(structure: AssistStructure): ParsedScreen {
        val fields = mutableListOf<ClassifiedField<AutofillId>>()
        val values = mutableMapOf<AutofillId, String>()
        var webDomain: String? = null
        var focused: AutofillId? = null
        fun walk(node: AssistStructure.ViewNode) {
            if (webDomain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { webDomain = it }
            val id = node.autofillId
            if (id != null && node.autofillType != View.AUTOFILL_TYPE_NONE && node.visibility == View.VISIBLE) {
                val signals = signalsOf(node)
                AutofillFieldClassifier.classify(signals)?.let { fields += ClassifiedField(id, it, signals) }
                if (node.isFocused) focused = id
                node.autofillValue?.takeIf { it.isText }?.textValue?.toString()?.let { values[id] = it }
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)
        val packageName = structure.activityComponent?.packageName.orEmpty()
        return ParsedScreen(AutofillForm(fields), AutofillMatcher.caller(packageName, webDomain), focused, values)
    }

    private fun signalsOf(node: AssistStructure.ViewNode): FieldSignals {
        val attributes = node.htmlInfo?.attributes.orEmpty()
            .mapNotNull { pair -> pair.first?.lowercase()?.let { it to pair.second.orEmpty() } }
            .toMap()
        return FieldSignals(
            autofillHints = node.autofillHints?.toList().orEmpty(),
            htmlAttributes = attributes,
            // Never the field's text: that is what the user typed, not what the field is.
            hint = listOfNotNull(node.hint, node.contentDescription?.toString()).joinToString(" ").ifBlank { null },
            idEntry = node.idEntry,
            inputType = node.inputType,
            options = node.autofillOptions?.map { it.toString() }.orEmpty(),
            maxLength = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) node.maxTextLength.takeIf { it > 0 } else null)
                ?: attributes["maxlength"]?.toIntOrNull(),
        )
    }
}
