package li.gkd.app.ui.page

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import li.gkd.app.resources.Res
import li.gkd.app.resources.feedback_app_issue_prefix
import li.gkd.app.resources.feedback_continue_suffix
import li.gkd.app.resources.feedback_scope
import li.gkd.app.resources.feedback_subscription_notice
import li.gkd.app.resources.feedback_thanks_prefix
import org.jetbrains.compose.resources.getString

suspend fun feedbackNotice(primary: Color): AnnotatedString = buildAnnotatedString {
    val highlight = SpanStyle(fontWeight = FontWeight.Bold, color = primary)
    append(getString(Res.string.feedback_thanks_prefix))
    withStyle(highlight) { append(getString(Res.string.feedback_scope)) }
    append("\n\n")
    append(getString(Res.string.feedback_subscription_notice))
    withStyle(highlight) { append(getString(Res.string.feedback_app_issue_prefix)) }
    append(getString(Res.string.feedback_continue_suffix))
}
