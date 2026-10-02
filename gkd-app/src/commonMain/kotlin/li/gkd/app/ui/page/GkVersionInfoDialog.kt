package li.gkd.app.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_close
import li.gkd.app.resources.build_channel
import li.gkd.app.resources.code_history
import li.gkd.app.resources.commit_time
import li.gkd.app.resources.version_code
import li.gkd.app.resources.version_info
import li.gkd.app.resources.version_name
import li.gkd.app.ui.component.GkAlertDialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkVersionInfoDialog(
    visible: Boolean,
    channel: String,
    versionCode: String,
    versionName: String,
    commitLabel: String,
    commitTime: String,
    onCommit: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (visible) {
        GkAlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(Res.string.version_info)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(text = stringResource(Res.string.build_channel))
                        Text(text = channel)
                    }
                    Column {
                        Text(text = stringResource(Res.string.version_code))
                        Text(text = versionCode)
                    }
                    Column {
                        Text(text = stringResource(Res.string.version_name))
                        Text(text = versionName)
                    }
                    Column {
                        Text(text = stringResource(Res.string.code_history))
                        Text(
                            modifier = Modifier.clickable(onClick = onCommit),
                            text = commitLabel,
                            color = MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                    Column {
                        Text(text = stringResource(Res.string.commit_time))
                        Text(text = commitTime)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(Res.string.action_close))
                }
            },
        )
    }
}

