package alibarznji.qasa.screens.tools.notifications

import alibarznji.qasa.R
import alibarznji.qasa.network.AppNotificationDTO
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaSheetHeader
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.text.DateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

/** iOS `AppNotificationDTO.title(locale:)`: the requested language, falling back to English. */
internal fun AppNotificationDTO.localizedTitle(code: String): String = when (code) {
    "ckb" -> title_ckb?.takeIf { it.isNotBlank() } ?: title_en
    "ar" -> title_ar?.takeIf { it.isNotBlank() } ?: title_en
    else -> title_en
}

internal fun AppNotificationDTO.localizedBody(code: String): String = when (code) {
    "ckb" -> body_ckb?.takeIf { it.isNotBlank() } ?: body_en
    "ar" -> body_ar?.takeIf { it.isNotBlank() } ?: body_en
    else -> body_en
}

/** iOS `date.formatted(date: .abbreviated, time: .shortened)`. */
internal fun formatNotificationDate(millis: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(millis))

/**
 * iOS `formatNotificationDate(_:)`: parses RFC 3339 with or without fractional seconds
 * and shows the raw string when it cannot, rather than hiding the date.
 */
internal fun formatNotificationDate(iso: String, locale: Locale): String =
    runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
        .map { formatNotificationDate(it, locale) }
        .getOrDefault(iso)

/** iOS `NotificationsView.notificationRow`: 8pt unread dot, title, body, date. */
@Composable
internal fun NotificationRow(
    title: String,
    body: String,
    date: String,
    unread: Boolean,
    onClick: () -> Unit,
) {
    val c = qasaColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.cardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = QasaDimens.cardPadding, vertical = QasaDimens.gapSmall),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
    ) {
        Box(
            Modifier
                .padding(top = QasaDimens.gapTiny)
                .size(QasaDimens.contentGap)
                .background(if (unread) c.accent else c.stroke, CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.spaceSmall)) {
            Text(title, color = c.textPrimary, style = qasaText.bodySemibold15)
            Text(body, color = c.textSecondary, style = qasaText.bodySmall)
            Text(date, color = c.textTertiary, style = qasaText.labelRegular)
        }
    }
}

/**
 * iOS `.onDelete`: swipe from the trailing edge to delete. Used by both sections; a
 * remote delete is per-user on the server (`DELETE /v1/notifications/{id}`).
 */
@Composable
internal fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = qasaColors
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.danger)
                    .padding(horizontal = QasaDimens.screenPadding),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = c.background,
                    modifier = Modifier.size(QasaIconSize.md),
                )
            }
        },
    ) { content() }
}

/** iOS detail `.sheet(item: $selected)`: the shared sheet header, then title, date and body. */
@Composable
internal fun NotificationDetailSheetContent(
    note: AppNotificationDTO,
    localeCode: String,
    locale: Locale,
    onClose: () -> Unit,
) {
    val c = qasaColors
    QasaSheetHeader(stringResource(R.string.notifications_detailtitle))
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(QasaDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
    ) {
        Text(note.localizedTitle(localeCode), color = c.textPrimary, style = qasaText.sectionHeadline)
        Text(formatNotificationDate(note.created_at, locale), color = c.textTertiary, style = qasaText.labelRegular)
        Text(note.localizedBody(localeCode), color = c.textSecondary, style = qasaText.bodyLarge)
    }
}
