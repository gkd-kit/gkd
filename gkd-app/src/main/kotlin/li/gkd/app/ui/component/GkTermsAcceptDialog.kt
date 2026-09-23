package li.gkd.app.ui.component

import li.gkd.app.MainViewModel

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.TimeUtils.throttle


@Composable
fun GkTermsAcceptDialog() {
    val mainVm = MainViewModel.requireCurrent()
    val context = LocalActivity.current as MainActivity
    val modifier = Modifier.fillMaxWidth()
    val stepDataList = remember {
        arrayOf(
            UiStrings.terms_notice to @Composable {
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
                Text(
                    modifier = modifier,
                    text = buildAnnotatedString {
                        append(UiStrings.terms_accept_prefix)
                        withLink(
                            LinkAnnotation.Url(
                                ShortUrlSet.URL12,
                                linkStyles
                            )
                        ) {
                            append(UiStrings.user_agreement)
                        }
                        append(UiStrings.terms_accept_conjunction)
                        withLink(
                            LinkAnnotation.Url(
                                ShortUrlSet.URL11,
                                linkStyles
                            )
                        ) {
                            append(UiStrings.privacy_policy)
                        }
                        append(UiStrings.terms_accept_suffix)
                    },
                )
            },
            UiStrings.a11y_about to @Composable {
                Text(
                    modifier = modifier,
                    text = UiStrings.a11y_usage_description,
                )
            }
        )
    }
    val step by mainVm.termsStepFlow.collectAsStateWithLifecycle()

    GkAlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stepDataList[step].first)
        },
        text = stepDataList[step].second,
        confirmButton = {
            TextButton(onClick = throttle {
                mainVm.acceptTermsStep(stepDataList.lastIndex)
            }) {
                Text(text = UiStrings.action_agree)
            }
        },
        dismissButton = {
            TextButton(onClick = throttle {
                context.finish()
            }) {
                Text(text = UiStrings.action_disagree)
            }
        }
    )
}
