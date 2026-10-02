package li.gkd.app.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_understood
import li.gkd.app.resources.page_help
import li.gkd.app.resources.privilege_project_name
import li.gkd.app.resources.privilege_service
import li.gkd.app.resources.privilege_service_help_description
import li.gkd.app.resources.privilege_service_project_prefix
import li.gkd.app.resources.privilege_service_project_suffix
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkPrivilegeTopBar(onBack: () -> Unit, onInfo: () -> Unit) {
    GkTopAppBar(
        navigationIcon = { GkIconButton(GkIcons.ArrowBack, onClick = onBack) },
        title = { Text(stringResource(Res.string.privilege_service)) },
        actions = {
            GkIconButton(
                GkIcons.Info,
                contentDescription = stringResource(Res.string.page_help),
                onClick = onInfo
            )
        })
}

@Composable
fun PrivilegeServiceInfoDialog(onDismissRequest: () -> Unit) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    GkAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(Res.string.privilege_service))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.privilege_service_help_description),
                )
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.privilege_service_project_prefix))
                        withLink(
                            LinkAnnotation.Url(
                                url = "https://github.com/priv-kit/priv-kit",
                                styles = linkStyles,
                            ),
                        ) {
                            append(stringResource(Res.string.privilege_project_name))
                        }
                        append(stringResource(Res.string.privilege_service_project_suffix))
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(Res.string.action_understood))
            }
        },
    )
}
