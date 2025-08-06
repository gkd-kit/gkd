package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.otherUserMapFlow
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Composable
fun AppNameText(
    appId: String? = null,
    appInfo: AppInfo? = null,
    fallbackName: String? = null,
) {
    val info = appInfo ?: appInfoCacheFlow.collectAsState().value[appId]
    val showSystemIcon = info?.isSystem == true
    val appName = (info?.name ?: fallbackName ?: appId ?: error("appId is required"))
    val userName = info?.userId?.let {
        val userInfo = otherUserMapFlow.collectAsState().value[info.userId]
        "「${userInfo?.name ?: info.userId}」"
    }
    if (!showSystemIcon && userName == null) {
        Text(
            text = appName,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.tertiary
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
            val textStyle = LocalTextStyle.current
            val contentColor = textStyle.color.takeOrElse { LocalContentColor.current }
            remember(textStyle, contentColor) {
                mapOf(
                    "icon" to InlineTextContent(
                        placeholder = Placeholder(
                            width = textStyle.fontSize,
                            height = textStyle.lineHeight,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(onClick = throttle { toast("当前是系统应用") })
                                .fillMaxSize(),
                            contentDescription = null,
                            tint = contentColor
                        )
                    }
                )
            }
        } else {
            emptyMap()
        }
        Text(
            text = annotatedString,
            inlineContent = inlineContent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}