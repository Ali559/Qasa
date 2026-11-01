package alibarznji.qasa.screens.vault.detail

import alibarznji.qasa.R
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.VaultPayload
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** iOS `detailCard(title:)` (`VaultItemDetailView.swift:216`): uppercase label over a card. */
@Composable
internal fun DetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(title)
        Spacer(Modifier.height(QasaDimens.gapSmall))
        CardContainer(content = content)
    }
}

/** iOS `organizationSection` (`VaultItemDetailView.swift:143`): colour, then collection. */
@Composable
internal fun OrganizationCard(payload: VaultPayload, collectionName: String?) {
    val c = qasaColors
    DetailCard(stringResource(R.string.vault_organization)) {
        val color = payload.organizationColor()
        InfoRow(stringResource(R.string.vault_color)) {
            if (color != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier.size(QasaIconSize.sm).background(color.swatch(), CircleShape),
                    )
                    Spacer(Modifier.width(QasaDimens.contentGap))
                    Text(stringResource(color.label), color = c.textPrimary, style = qasaText.bodyMedium15)
                }
            } else {
                Text(stringResource(R.string.common_none), color = c.textSecondary, style = qasaText.bodyMedium15)
            }
        }
        HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
        InfoRow(stringResource(R.string.vault_collection)) {
            Text(
                collectionName ?: stringResource(R.string.common_none),
                color = if (collectionName == null) c.textSecondary else c.textPrimary,
                style = qasaText.bodyMedium15,
            )
        }
    }
}

/** iOS `historySection` (`VaultItemDetailView.swift:190`). */
@Composable
internal fun HistoryCard(createdAt: Long?, updatedAt: Long?, lastUsedAt: Long?) {
    val c = qasaColors
    DetailCard(stringResource(R.string.vault_history)) {
        HistoryRow(R.string.vault_created, createdAt)
        HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
        HistoryRow(R.string.vault_lastupdated, updatedAt)
        HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
        HistoryRow(R.string.vault_lastused, lastUsedAt)
    }
}

@Composable
private fun HistoryRow(@StringRes label: Int, millis: Long?) {
    val c = qasaColors
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    InfoRow(stringResource(label)) {
        Text(
            millis?.let { VaultDateFormat.display(context, locale, it) } ?: stringResource(R.string.vault_never),
            color = c.textPrimary,
            style = qasaText.bodyMedium15,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    val c = qasaColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = QasaDimens.gapSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = c.textSecondary, style = qasaText.bodyMedium)
        Spacer(Modifier.width(QasaDimens.gap))
        value()
    }
}

/** iOS `NoteColor` (`Models/VaultEnums.swift:219`): raw names match the payload's `color`. */
internal enum class NoteColor(val raw: String, @StringRes val label: Int) {
    Mint("mint", R.string.color_mint),
    Gold("gold", R.string.color_gold),
    Blue("blue", R.string.color_blue),
    Purple("purple", R.string.color_purple),
    Red("red", R.string.color_red),
    Gray("gray", R.string.color_gray),
    ;

    @Composable
    fun swatch(): Color {
        val c = qasaColors
        return when (this) {
            Mint -> Color(0xFF2BB58A) // the note colour named mint, not the accent
            Gold -> c.gold
            Blue -> Color(0xFF3B82F6)
            Purple -> Color(0xFFA855F7)
            Red -> c.danger
            Gray -> c.textSecondary
        }
    }

    companion object {
        fun fromRaw(raw: String): NoteColor? = entries.firstOrNull { it.raw == raw.lowercase() }
    }
}

/** iOS `organizationColor`: notes and cards only. */
internal fun VaultPayload.organizationColor(): NoteColor? = when (this) {
    is VaultPayload.SecureNote -> NoteColor.fromRaw(color)
    is VaultPayload.Card -> NoteColor.fromRaw(color)
    else -> null
}

/**
 * iOS `VaultDateFormat.display` (`VaultItemDetailView.swift:308`): relative under a day
 * ("Just now", "5 minutes ago", "1 hour ago"), otherwise abbreviated date + short time.
 */
internal object VaultDateFormat {
    fun display(context: Context, locale: Locale, millis: Long, now: Long = System.currentTimeMillis()): String {
        val seconds = (now - millis) / 1000
        return when {
            seconds < 0 -> absolute(locale, millis)
            seconds < 60 -> context.getString(R.string.date_justnow)
            seconds < 3600 -> (seconds / 60).toInt().let {
                if (it == 1) context.getString(R.string.date_minuteago) else context.getString(R.string.date_minutesago, it)
            }
            seconds < 24 * 3600 -> (seconds / 3600).toInt().let {
                if (it == 1) context.getString(R.string.date_hourago) else context.getString(R.string.date_hoursago, it)
            }
            else -> absolute(locale, millis)
        }
    }

    private fun absolute(locale: Locale, millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(millis))
}
