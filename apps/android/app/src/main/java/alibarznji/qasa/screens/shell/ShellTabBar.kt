package alibarznji.qasa.screens.shell

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight

/** The four destinations that replaced the side drawer in the calm + personal redesign. */
enum class ShellTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Outlined.Home),
    Passwords(R.string.tab_passwords, Icons.Outlined.Key),
    Alerts(R.string.tab_alerts, Icons.Outlined.NotificationsActive),
    You(R.string.tab_you, Icons.Outlined.PersonOutline),
}

/** Card-coloured bar with a hairline on top; the selected tab takes the accent colour. */
@Composable
fun ShellTabBar(selected: ShellTab, onSelect: (ShellTab) -> Unit) {
    val c = qasaColors
    Column(Modifier.fillMaxWidth().background(c.cardBackground).navigationBarsPadding()) {
        HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = QasaDimens.contentGap)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            ShellTab.entries.forEach { tab ->
                val on = tab == selected
                val tint = if (on) c.accent else c.textTertiary
                Column(
                    Modifier
                        .weight(1f)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(vertical = QasaDimens.spaceSmall),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(QasaIconSize.xxl))
                    Spacer(Modifier.height(QasaDimens.spaceSmall))
                    Text(
                        stringResource(tab.labelRes),
                        color = tint,
                        style = qasaText.labelRegular.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
