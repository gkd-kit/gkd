package li.gkd.app.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_about
import li.gkd.app.resources.a11y_usage_description
import li.gkd.app.resources.action_agree
import li.gkd.app.resources.action_disagree
import li.gkd.app.resources.privacy_policy
import li.gkd.app.resources.terms_accept_conjunction
import li.gkd.app.resources.terms_accept_prefix
import li.gkd.app.resources.terms_accept_suffix
import li.gkd.app.resources.terms_notice
import li.gkd.app.resources.user_agreement
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkTermsAcceptDialog(
    onAccept: suspend () -> Unit,
    onError: (Exception) -> Unit,
    onDisagree: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val onAgree: () -> Unit = {
        if (step == 0) step = 1
        else scope.launch {
            try {
                onAccept()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
    val modifier = Modifier.fillMaxWidth()
    val stepDataList = arrayOf(
        stringResource(Res.string.terms_notice) to @Composable {
            val linkStyles = TextLinkStyles(
                style = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            )
            Text(
                modifier = modifier,
                text = buildAnnotatedString {
                    append(stringResource(Res.string.terms_accept_prefix))
                    withLink(
                        LinkAnnotation.Url(
                            AppLinks.TermsOfService,
                            linkStyles
                        )
                    ) {
                        append(stringResource(Res.string.user_agreement))
                    }
                    append(stringResource(Res.string.terms_accept_conjunction))
                    withLink(
                        LinkAnnotation.Url(
                            AppLinks.PrivacyPolicy,
                            linkStyles
                        )
                    ) {
                        append(stringResource(Res.string.privacy_policy))
                    }
                    append(stringResource(Res.string.terms_accept_suffix))
                },
            )
        },
        stringResource(Res.string.a11y_about) to @Composable {
            Text(
                modifier = modifier,
                text = stringResource(Res.string.a11y_usage_description),
            )
        }
    )

    GkAlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stepDataList[step].first)
        },
        text = stepDataList[step].second,
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text(text = stringResource(Res.string.action_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = onDisagree) {
                Text(text = stringResource(Res.string.action_disagree))
            }
        }
    )
}
