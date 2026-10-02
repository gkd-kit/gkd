package li.gkd.app.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_label
import li.gkd.app.resources.a11y_whitelist_open
import li.gkd.app.resources.about_title
import li.gkd.app.resources.action_toast
import li.gkd.app.resources.action_toast_style_system
import li.gkd.app.resources.advanced_settings
import li.gkd.app.resources.backup_restore
import li.gkd.app.resources.dynamic_colors
import li.gkd.app.resources.hide_from_recents
import li.gkd.app.resources.hide_from_recents_description
import li.gkd.app.resources.partial_disable_setup_open
import li.gkd.app.resources.privilege_service_disconnected
import li.gkd.app.resources.service_partial_disable
import li.gkd.app.resources.service_partial_disable_description
import li.gkd.app.resources.settings_appearance
import li.gkd.app.resources.settings_general
import li.gkd.app.resources.settings_other
import li.gkd.app.resources.theme_mode
import li.gkd.app.resources.turned_off
import li.gkd.app.resources.whitelist_title
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextMenu
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.option.DarkThemeOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.style.titleItemPadding
import org.jetbrains.compose.resources.stringResource

data class SettingsUiState(
    val toastWhenClick: Boolean,
    val useSystemToast: Boolean,
    val actionToast: String,
    val excludeFromRecents: Boolean,
    val enableBlockA11yAppList: Boolean,
    val enableDarkTheme: Boolean?,
    val enableDynamicColor: Boolean,
    val privilegeAvailable: Boolean,
    val dynamicColorAvailable: Boolean,
)

enum class SettingsDestination { ActionToast, BlockA11ySetup, BlockA11yAppList, Advanced, About }

data class SettingsUiActions(
    val onNavigate: (SettingsDestination) -> Unit,
    val onExcludeFromRecents: (Boolean) -> Unit,
    val onBlockA11yEnabled: (Boolean) -> Unit,
    val onDarkTheme: (Boolean?) -> Unit,
    val onDynamicColor: (Boolean) -> Unit,
    val onBackup: () -> Unit,
)

@Composable
fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsUiActions,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    notificationItem: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(scrollState)
    ) {

        Text(
            text = stringResource(Res.string.settings_general),
            modifier = Modifier.titleItemPadding(showTop = false),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        GkSettingItem(
            title = stringResource(Res.string.action_toast),
            subtitle = listOf(
                if (!state.toastWhenClick) stringResource(Res.string.turned_off)
                else if (state.useSystemToast) stringResource(Res.string.action_toast_style_system)
                else "",
                state.actionToast.lineSequence().joinToString(" ").trim(),
            ).filter { it.isNotEmpty() }.joinToString(" · "),
            subtitleMaxLines = 1,
            subtitleOverflow = TextOverflow.Ellipsis,
            onClick = { actions.onNavigate(SettingsDestination.ActionToast) },
        )

        notificationItem()

        GkTextSwitch(
            title = stringResource(Res.string.hide_from_recents),
            subtitle = stringResource(Res.string.hide_from_recents_description),
            checked = state.excludeFromRecents,
            onCheckedChange = actions.onExcludeFromRecents,
        )

        AnimatedVisibility(visible = state.enableBlockA11yAppList) {
            Text(
                text = stringResource(Res.string.a11y_label),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        GkTextSwitch(
            title = stringResource(Res.string.service_partial_disable),
            subtitle = if (state.enableBlockA11yAppList && !state.privilegeAvailable)
                stringResource(Res.string.privilege_service_disconnected)
            else stringResource(Res.string.service_partial_disable_description),
            checked = state.enableBlockA11yAppList,
            onClick = { actions.onNavigate(SettingsDestination.BlockA11ySetup) },
            onClickLabel = stringResource(Res.string.partial_disable_setup_open),
            onCheckedChange = {
                if (it) {
                    actions.onNavigate(SettingsDestination.BlockA11ySetup)
                } else {
                    actions.onBlockA11yEnabled(false)
                }
            },
        )
        AnimatedVisibility(visible = state.enableBlockA11yAppList) {
            GkSettingItem(
                title = stringResource(Res.string.whitelist_title),
                onClickLabel = stringResource(Res.string.a11y_whitelist_open),
                onClick = {
                    actions.onNavigate(SettingsDestination.BlockA11yAppList)
                })
        }

        Text(
            text = stringResource(Res.string.settings_appearance),
            modifier = Modifier.titleItemPadding(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        GkTextMenu(
            title = stringResource(Res.string.theme_mode),
            option = DarkThemeOption.objects.findOption(state.enableDarkTheme),
            onOptionChange = {
                actions.onDarkTheme(it.value)
            }
        )

        if (state.dynamicColorAvailable) {
            GkTextSwitch(
                title = stringResource(Res.string.dynamic_colors),
                checked = state.enableDynamicColor,
                onCheckedChange = {
                    actions.onDynamicColor(it)
                }
            )
        }

        Text(
            text = stringResource(Res.string.settings_other),
            modifier = Modifier.titleItemPadding(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        GkSettingItem(title = stringResource(Res.string.advanced_settings), onClick = {
            actions.onNavigate(SettingsDestination.Advanced)
        })
        GkSettingItem(title = stringResource(Res.string.backup_restore), onClick = {
            actions.onBackup()
        })

        GkSettingItem(title = stringResource(Res.string.about_title), onClick = {
            actions.onNavigate(SettingsDestination.About)
        })

        GkPageBottomSpace()
    }
}
