package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.resources.Res
import li.gkd.app.resources.update_success
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.navigation.EditorFrame
import li.gkd.app.ui.page.NotificationTextScreen
import li.gkd.app.ui.text.getSync

@Composable
fun NotificationTextPage(showToast: (String) -> Unit, editorFrame: EditorFrame) {
    val initial = remember { settings.value }
    val rules by ruleGroupState.collectAsStateWithLifecycle()
    val count by SettingsRepository.actionCount.collectAsStateWithLifecycle()
    val groups = rules.value?.groups
    NotificationTextScreen(
        initialEnabled = initial.useCustomNotifText,
        initialTitle = initial.customNotifTitle,
        initialText = initial.customNotifText,
        renderPreview = { text ->
            HomeDataText.format(
                text, groups?.globalGroups?.size,
                groups?.appSize, groups?.appGroupSize, count
            )
        },
        onSave = { enabled, title, text ->
            if (SettingsRepository.saveNotificationText(
                    enabled,
                    title,
                    text
                )
            ) showToast(Res.string.update_success.getSync())
        },
        frame = { editor, content -> editorFrame(editor, null, content) },
    )
}
