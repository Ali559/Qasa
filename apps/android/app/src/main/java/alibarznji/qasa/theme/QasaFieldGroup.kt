package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.flow.drop

/*
 * `design/README.md` rule 4, "Labelled fields are rows in a grouped card" (iOS Contacts).
 *
 *   QasaFieldGroup {            // one card per form section, 16 apart (the caller's gap)
 *       QasaTextRow(...)        // label on top, value under it
 *       QasaSecureRow(...)      // + reveal (and any extra trailing control)
 *       QasaMenuRow(...)        // label, chosen value, chevron; opens a menu
 *       QasaDateRow(...)        // typed date, calendar button, inline picker
 *       QasaGroupRow(...) { }   // the bare row frame for anything else (2FA)
 *   }
 *   QasaNotesCard(...)          // multi-line notes: their own card under the group
 *
 * Rows register their label and error with the enclosing group, which draws the dividers
 * between them and the error messages under the card ("Email: …" when it has more rows).
 */

// ---------------------------------------------------------------------------
// Group
// ---------------------------------------------------------------------------

/** One row's entry in its group: what the message under the card needs. */
@Stable
internal class GroupRowEntry {
    var label by mutableStateOf("")
    var error by mutableStateOf<String?>(null)

    /** The row's top inside the card, so messages list in the order the rows read. */
    var top by mutableFloatStateOf(0f)
}

@Stable
internal class FieldGroupState {
    val rows = mutableStateListOf<GroupRowEntry>()
}

private val LocalFieldGroup = compositionLocalOf<FieldGroupState?> { null }

/**
 * The card one form section's rows share: `cardBackground`, 1dp `stroke`, `radius.group`
 * (20), a 1dp divider between rows inset `space.groupDividerInset` on the leading side.
 * Row errors are listed `space.fieldErrorGap` under the card in `type.fieldError` `danger`.
 * Successive groups are spaced `space.formGap` (16) by their parent column.
 */
@Composable
fun QasaFieldGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = qasaColors
    val state = remember { FieldGroupState() }
    val shape = RoundedCornerShape(QasaTokens.Radius.group)
    // Divider positions, written by the layout below and read when it draws.
    val dividers = remember { mutableStateOf(emptyList<Float>()) }
    Column(modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalFieldGroup provides state) {
            Layout(
                content = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.cardBackground, shape)
                    .drawWithContent {
                        drawContent()
                        val stroke = QasaDimens.stroke.toPx()
                        val inset = QasaTokens.Space.groupDividerInset.toPx()
                        val rtl = layoutDirection == LayoutDirection.Rtl
                        dividers.value.forEach { y ->
                            drawRect(
                                c.stroke,
                                topLeft = Offset(if (rtl) 0f else inset, y - stroke / 2),
                                size = Size(size.width - inset, stroke),
                            )
                        }
                    }
                    .border(QasaDimens.stroke, c.stroke, shape),
            ) { measurables, constraints ->
                val rowConstraints = constraints.copy(minHeight = 0)
                val placeables = measurables.map { it.measure(rowConstraints) }
                val width = constraints.maxWidth
                val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
                layout(width, height) {
                    var y = 0
                    val lines = ArrayList<Float>()
                    placeables.forEach { p ->
                        if (p.height == 0) return@forEach
                        if (y > 0) lines += y.toFloat()
                        p.placeRelative(0, y)
                        y += p.height
                    }
                    if (lines != dividers.value) dividers.value = lines
                }
            }
        }
        val prefixed = state.rows.size > 1
        val errors = state.rows.filter { it.error != null }.sortedBy { it.top }
        if (errors.isNotEmpty()) {
            Spacer(Modifier.height(QasaTokens.Space.fieldErrorGap))
            errors.forEach { row ->
                val message = row.error.orEmpty()
                QasaFieldErrorText(if (prefixed && row.label.isNotEmpty()) "${row.label}: $message" else message)
            }
        }
    }
}

/** A field's rule message: `type.fieldError` (11) in `danger`. */
@Composable
fun QasaFieldErrorText(message: String, modifier: Modifier = Modifier) {
    Text(message, color = qasaColors.danger, style = qasaText.fieldError, modifier = modifier.fillMaxWidth())
}

/** Registers a row with its enclosing [QasaFieldGroup] (if any); returns the modifier
 *  that records the row's position. */
@Composable
private fun registerWithGroup(label: String, error: String?): Modifier {
    val group = LocalFieldGroup.current ?: return Modifier
    val entry = remember { GroupRowEntry() }
    DisposableEffect(group) {
        group.rows += entry
        onDispose { group.rows -= entry }
    }
    SideEffect {
        entry.label = label
        entry.error = error
    }
    return Modifier.onPlaced { entry.top = it.positionInParent().y }
}

/** Draws the `size.groupErrorStripe` `danger` bar down the leading edge (mirrors in RTL). */
private fun Modifier.errorStripe(color: Color?): Modifier =
    if (color == null) this else drawWithContent {
        drawContent()
        val w = QasaTokens.Size.groupErrorStripe.toPx()
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
        drawRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height))
    }

/** The row label: `type.groupLabel`; `danger` with an error, `accent` while focused. */
@Composable
private fun GroupLabel(label: String, error: Boolean, focused: Boolean) {
    val c = qasaColors
    val color = animateQasaColor(
        when {
            error -> c.danger
            focused -> c.accent
            else -> c.textSecondary
        },
    )
    Text(
        label,
        color = color,
        style = qasaText.groupLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // The input carries the label as its name; the visible text is not a second node.
        modifier = Modifier.clearAndSetSemantics {},
    )
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

/**
 * The row frame every grouped field draws: at least `size.groupRowMinHeight` (60), padded
 * `space.groupRowVertical` × `space.groupRowHorizontal`; the label, then [content] under it
 * with no gap; [trailing] controls at the trailing edge, vertically centered. [below] spans
 * the row's full width under that (the inline calendar). A row with [error] gets the
 * leading `danger` stripe and a `danger` label; the message shows under the card.
 *
 * [onTap] runs for a tap anywhere in the row (text rows focus their input). When [role] is
 * set the whole row is one accessible control (pickers), named by its label and value.
 */
@Composable
fun QasaGroupRow(
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    focused: Boolean = false,
    onTap: (() -> Unit)? = null,
    role: Role? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = qasaColors
    val placed = registerWithGroup(label, error)
    val tap = when {
        onTap == null -> Modifier
        role != null -> Modifier.clickable(role = role, onClick = onTap)
        // No semantics node of its own: the input inside is the control.
        else -> Modifier.pointerInput(onTap) { detectTapGestures { onTap() } }
    }
    Column(
        modifier
            .fillMaxWidth()
            .then(placed)
            .then(tap)
            .errorStripe(if (error != null) c.danger else null),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = QasaTokens.Size.groupRowMinHeight)
                .padding(
                    horizontal = QasaTokens.Space.groupRowHorizontal,
                    vertical = QasaTokens.Space.groupRowVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                GroupLabel(label, error = error != null, focused = focused)
                content()
            }
            if (trailing != null) {
                Spacer(Modifier.width(QasaTokens.Space.groupRowTrailingGap))
                trailing()
            }
        }
        below?.invoke(this)
    }
}

/** A 12 `textSecondary` caption inside a row, under its value (the localized date, a hint). */
@Composable
fun QasaRowCaption(text: String, color: Color = qasaColors.textSecondary) {
    Text(text, color = color, style = qasaText.rowSubtitle)
}

/** The hint under an empty input: `textPlaceholder`, one line, the value style. */
@Composable
private fun RowPlaceholder(text: String) {
    Text(
        text,
        color = qasaColors.textPlaceholder,
        style = qasaText.groupValue,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A labelled text input as a group row. [placeholder] is an example ("name@example.com"),
 * never the label; empty means no hint. [prefix] is fixed text before the input in
 * `accent` (the phone row's dial code). [caption] is a 12 `textSecondary` line under the
 * input. Keyboard type, IME action and actions are passed through unchanged.
 */
@Composable
fun QasaTextRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = keyboardOptions.imeAction,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    prefix: String? = null,
    caption: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    trailing: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    TextRowFrame(label, error, prefix, caption, focusRequester, trailing, below, modifier) { interaction, fieldModifier ->
        val c = qasaColors
        val activity = LocalUserActivity.current
        BasicTextField(
            value = value,
            onValueChange = {
                activity.noteActivity()
                onValueChange(it)
            },
            singleLine = true,
            textStyle = qasaText.groupValue.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions.copy(imeAction = imeAction),
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = fieldModifier,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) RowPlaceholder(placeholder)
                    inner()
                }
            },
        )
    }
}

/**
 * [QasaTextRow] over a [TextFieldValue], for rows that reformat what is typed (card number,
 * expiry) and so place the cursor themselves.
 */
@Composable
fun QasaTextRow(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() },
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    TextRowFrame(label, error, null, null, focusRequester, trailing, null, modifier) { interaction, fieldModifier ->
        val c = qasaColors
        val activity = LocalUserActivity.current
        BasicTextField(
            value = value,
            onValueChange = {
                activity.noteActivity()
                onValueChange(it)
            },
            singleLine = true,
            textStyle = qasaText.groupValue.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = fieldModifier,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty() && placeholder.isNotEmpty()) RowPlaceholder(placeholder)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun TextRowFrame(
    label: String,
    error: String?,
    prefix: String?,
    caption: String?,
    focusRequester: FocusRequester,
    trailing: (@Composable RowScope.() -> Unit)?,
    below: (@Composable ColumnScope.() -> Unit)?,
    modifier: Modifier,
    field: @Composable (MutableInteractionSource, Modifier) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    QasaGroupRow(
        label = label,
        modifier = modifier,
        error = error,
        focused = focused,
        onTap = {
            focusRequester.requestFocus()
            keyboard?.show()
        },
        trailing = trailing,
        below = below,
    ) {
        val fieldModifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            // TalkBack names the input by the row's label (the visible label is hidden).
            .semantics { contentDescription = label }
        if (prefix != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prefix,
                    color = qasaColors.accent,
                    style = qasaText.groupValue,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.width(QasaDimens.contentGap))
                Box(Modifier.weight(1f)) {
                    field(interaction, fieldModifier)
                }
            }
        } else {
            field(interaction, fieldModifier)
        }
        if (caption != null) QasaRowCaption(caption)
    }
}

/**
 * A masked [QasaTextRow] with the reveal (eye) toggle at the trailing edge; [trailing]
 * goes before it (the editor's generate wand). [revealed] / [onToggleReveal] let a screen
 * own the state (one toggle for two rows); left null, the row keeps its own.
 */
@Composable
fun QasaSecureRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    revealed: Boolean? = null,
    onToggleReveal: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = keyboardOptions.imeAction,
    focusRequester: FocusRequester = remember { FocusRequester() },
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    var ownRevealed by rememberSaveable { mutableStateOf(false) }
    val isRevealed = revealed ?: ownRevealed
    val toggle = onToggleReveal ?: { ownRevealed = !ownRevealed }
    val keyboard = LocalSoftwareKeyboardController.current
    QasaTextRow(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        imeAction = imeAction,
        visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
        focusRequester = focusRequester,
        trailing = {
            trailing?.invoke(this)
            FieldIconButton(
                icon = if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = stringResource(
                    if (isRevealed) R.string.a11y_hidepassword else R.string.a11y_showpassword,
                ),
                onClick = {
                    keyboard?.hide()
                    toggle()
                },
            )
        },
    )
}

/**
 * A picker as a group row: the label, the chosen [value] (or [placeholder] when empty) and
 * a trailing [RowChevron]; tapping the row runs [onClick]. [trailing] sits before the
 * chevron (the collection picker's "new collection" button). [below] hosts a menu.
 */
@Composable
fun QasaPickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = qasaColors
    QasaGroupRow(
        label = label,
        // One control for TalkBack: "<label>, <value>, drop-down list".
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
        error = error,
        onTap = onClick,
        role = Role.DropdownList,
        trailing = {
            trailing?.invoke(this)
            RowChevron()
        },
        below = below,
    ) {
        if (value.isEmpty()) {
            RowPlaceholder(placeholder)
        } else {
            Text(
                value,
                color = c.textPrimary,
                style = qasaText.groupValue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A [QasaPickerRow] that opens a menu of [options] under the row. */
@Composable
fun <T> QasaMenuRow(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = qasaColors
    var expanded by remember { mutableStateOf(false) }
    QasaPickerRow(
        label = label,
        value = selectedLabel,
        onClick = { expanded = true },
        modifier = modifier,
        error = error,
        trailing = trailing,
        below = {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = c.cardBackground,
                modifier = Modifier.heightIn(max = MenuMaxHeight),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), color = c.textPrimary, style = qasaText.field) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        },
    )
}

/** The menu's height cap (was `MenuPicker`'s). */
private val MenuMaxHeight = 360.dp

/**
 * A date as a group row: typed text with the date-format [placeholder], a trailing calendar
 * button that opens the calendar inline under the input, and [localized] (the stored date in
 * the user's locale) as a caption once the text is a valid date. Stored dates are ISO
 * `YYYY-MM-DD` (the shared rules, `validate.rs`); the calendar writes that form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QasaDateRow(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    /** [text] as a date, for the calendar's initial selection. */
    date: LocalDate? = null,
    localized: String? = null,
) {
    val c = qasaColors
    var open by rememberSaveable { mutableStateOf(false) }
    QasaTextRow(
        label = label,
        value = text,
        onValueChange = onTextChange,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        caption = localized?.takeIf { it != text.trim() },
        trailing = {
            FieldIconButton(
                icon = Icons.Default.CalendarMonth,
                contentDescription = stringResource(R.string.date_pick),
                onClick = { open = !open },
                tint = c.accent,
            )
        },
        below = if (!open) null else {
            {
                val initialUtc = remember { (date ?: LocalDate.now()).toEpochDay() * MILLIS_PER_DAY }
                val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
                LaunchedEffect(state) {
                    // Material's DatePicker speaks UTC-midnight millis: the picked calendar day.
                    snapshotFlow { state.selectedDateMillis }.drop(1).collect { utc ->
                        if (utc != null) {
                            onTextChange(LocalDate.ofEpochDay(Math.floorDiv(utc, MILLIS_PER_DAY)).toString())
                        }
                    }
                }
                DatePicker(
                    state = state,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { open = false },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = QasaTokens.Space.groupRowHorizontal),
                ) { Text(stringResource(R.string.date_done), color = c.accent, style = qasaText.button) }
            }
        },
    )
}

private const val MILLIS_PER_DAY = 86_400_000L

// ---------------------------------------------------------------------------
// Notes
// ---------------------------------------------------------------------------

/**
 * Multi-line notes in their own card under the group: `radius.control` (10), `cardBackground`,
 * 1dp `stroke`, the label inside `space.groupNotesLabelTop` (9) from the top, the text under
 * it; at least `size.notesMinHeight` (112), growing (to 12 lines, then it scrolls). Tapping
 * anywhere focuses the text. An [error] shows the stripe and the message under the card.
 */
@Composable
fun QasaNotesCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = qasaColors
    val activity = LocalUserActivity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(QasaDimens.controlRadius)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = QasaDimens.notesMinHeight)
                .clip(shape)
                .background(c.cardBackground, shape)
                .errorStripe(if (error != null) c.danger else null)
                .border(QasaDimens.stroke, c.stroke, shape)
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                }
                .padding(horizontal = QasaTokens.Space.groupRowHorizontal)
                .padding(top = QasaTokens.Space.groupNotesLabelTop, bottom = QasaTokens.Space.groupRowVertical),
        ) {
            GroupLabel(label, error = error != null, focused = focused)
            BasicTextField(
                value = value,
                onValueChange = {
                    activity.noteActivity()
                    onValueChange(it)
                },
                singleLine = false,
                minLines = NOTES_MIN_LINES,
                maxLines = NOTES_MAX_LINES,
                textStyle = qasaText.groupValue.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = keyboardOptions,
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.TopStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = c.textPlaceholder, style = qasaText.groupValue)
                        }
                        inner()
                    }
                },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(QasaTokens.Space.fieldErrorGap))
            QasaFieldErrorText(error)
        }
    }
}

/** Three lines fill the 112 card under its label; up to 12, then it scrolls. */
private const val NOTES_MIN_LINES = 3
private const val NOTES_MAX_LINES = 12
