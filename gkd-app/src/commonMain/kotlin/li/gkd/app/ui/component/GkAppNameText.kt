package li.gkd.app.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.model.AppInfo
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_profile_name
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkAppNameText(
    appName: String,
    showSystemIcon: Boolean = false,
    userName: String? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    if (!showSystemIcon && userName == null) {
        Text(
            modifier = modifier,
            text = appName,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = style,
            color = color,
        )
    } else {
        val userNameColor = MaterialTheme.colorScheme.tertiary
        val annotatedString = remember(showSystemIcon, appName, userName, userNameColor) {
            buildAnnotatedString {
                if (showSystemIcon) {
                    appendInlineContent("icon")
                }
                append(appName)
                if (userName != null) {
                    append(" ")
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = userNameColor,
                        )
                    ) {
                        append(userName)
                    }
                }
            }
        }
        val inlineContent = if (showSystemIcon) {
            val contentColor = style.color.takeOrElse { LocalContentColor.current }
            remember(style, contentColor) {
                mapOf(
                    "icon" to InlineTextContent(
                        placeholder = Placeholder(
                            width = style.fontSize,
                            height = style.lineHeight,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        GkIcon(
                            imageVector = GkIcons.VerifiedUser,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .fillMaxSize(),
                            tint = contentColor
                        )
                    }
                )
            }
        } else {
            emptyMap()
        }
        Text(
            modifier = modifier,
            text = annotatedString,
            inlineContent = inlineContent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = style,
            color = color,
        )
    }
}

@Composable
fun GkAppNameText(
    appId: String? = null,
    fallbackName: String? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    appInfo: AppInfo? = null,
) {
    val catalog = AppInfoRepository.state.collectAsStateWithLifecycle().value.snapshot
    val info =
        appInfo ?: catalog?.apps?.get(appId)
    val showSystemIcon = info?.isSystem == true
    val appName = (info?.name ?: fallbackName ?: appId ?: error("appId is required"))
    val userName = info?.userId?.let { userId ->
        if (userId == catalog?.inventory?.userId) {
            null
        } else {
            val userInfo =
                catalog?.users?.get(userId)
            stringResource(Res.string.app_profile_name, userInfo?.name ?: userId.toString())
        }
    }
    GkAppNameText(
        appName = appName,
        showSystemIcon = showSystemIcon,
        userName = userName,
        modifier = modifier,
        style = style,
        color = color,
    )
}
