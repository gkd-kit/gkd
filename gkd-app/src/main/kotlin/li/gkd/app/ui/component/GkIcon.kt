package li.gkd.app.ui.component

import li.gkd.app.text.UiStrings
import androidx.compose.animation.core.tween
import li.songe.morph.compose.AnimatedMorphIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

@Composable
fun GkIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = getIconDefaultDesc(imageVector),
    animateMorph: Boolean = false,
) {
    if (animateMorph) {
        AnimatedMorphIcon(
            imageVector = imageVector,
            modifier = modifier,
            contentDescription = contentDescription,
            tint = tint,
            animationSpec = tween(300),
        )
    } else {
        Icon(
            imageVector = imageVector,
            modifier = modifier,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Composable
fun GkIconButton(
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    contentDescription: String? = getIconDefaultDesc(imageVector),
    onClickLabel: String? = null,
    animateMorph: Boolean = false,
) = GkTooltipIconButtonBox(
    contentDescription = contentDescription,
) {
    IconButton(
        modifier = modifier.semantics {
            if (onClickLabel != null) {
                this.onClick(label = onClickLabel, action = null)
            }
        },
        enabled = enabled,
        onClick = onClick,
        colors = colors,
    ) {
        GkIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            animateMorph = animateMorph,
        )
    }
}

fun getIconDefaultDesc(imageVector: ImageVector): String? = when (imageVector) {
    GkIcons.Add -> UiStrings.action_add
    GkIcons.Edit -> UiStrings.action_edit
    GkIcons.Delete -> UiStrings.action_delete
    GkIcons.Share -> UiStrings.action_share
    GkIcons.Settings -> UiStrings.settings_title
    GkIcons.Close -> UiStrings.action_close
    GkIcons.ArrowBack -> UiStrings.action_back
    GkIcons.HelpOutline -> UiStrings.help_title
    GkIcons.ToggleOff -> UiStrings.action_close
    GkIcons.ToggleOn -> UiStrings.action_turn_on
    GkIcons.History -> UiStrings.history_title
    GkIcons.Sort -> UiStrings.sort_filter
    GkIcons.OpenInNew -> UiStrings.open_new_page
    GkIcons.ContentCopy -> UiStrings.text_copy
    GkIcons.MoreVert -> UiStrings.more_actions
    else -> null
}

object GkIcons {
    val PlayArrow get() = Icons.Filled.PlayArrow
    val Link get() = Icons.Outlined.Link
    val CheckCircle get() = Icons.Outlined.CheckCircle
    val RemoveCircleOutline get() = Icons.Outlined.RemoveCircleOutline
    val StackedDocuments get() = li.gkd.app.ui.icon.StackedDocuments
    val PageInfo get() = li.gkd.app.ui.icon.PageInfo
    val FlashOn get() = li.gkd.app.ui.icon.FlashOn
    val FlashOff get() = li.gkd.app.ui.icon.FlashOff
    val Block get() = Icons.Default.Block
    val History get() = Icons.Default.History
    val Sort get() = Icons.AutoMirrored.Filled.Sort
    val Add get() = Icons.Outlined.Add
    val KeyboardArrowRight get() = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val ContentCopy get() = Icons.Outlined.ContentCopy
    val MoreVert get() = Icons.Default.MoreVert
    val ArrowBack get() = Icons.AutoMirrored.Filled.ArrowBack
    val Android get() = li.gkd.app.ui.icon.AndroidHead
    val Edit get() = Icons.Outlined.Edit
    val Share get() = Icons.Default.Share
    val Delete get() = Icons.Outlined.Delete
    val Close get() = Icons.Default.Close
    val OpenInNew get() = Icons.AutoMirrored.Outlined.OpenInNew
    val Settings get() = Icons.Outlined.Settings
    val Home get() = Icons.Outlined.Home
    val FormatListBulleted get() = Icons.AutoMirrored.Filled.FormatListBulleted
    val Apps get() = Icons.Default.Apps
    val Info get() = Icons.Outlined.Info
    val Flowchart get() = li.gkd.app.ui.icon.Flowchart
    val ToggleOff get() = Icons.Outlined.ToggleOff
    val ToggleOn get() = Icons.Outlined.ToggleOn
    val HelpOutline get() = Icons.AutoMirrored.Outlined.HelpOutline
    val ArrowForward get() = Icons.AutoMirrored.Filled.ArrowForward
    val Image get() = Icons.Outlined.Image
    val WarningAmber get() = Icons.Default.WarningAmber
    val RocketLaunch get() = Icons.Outlined.RocketLaunch
    val CenterFocusWeak get() = Icons.Default.CenterFocusWeak
    val AutoMode get() = Icons.Outlined.AutoMode
    val BrightnessAuto get() = Icons.Outlined.BrightnessAuto
    val LightMode get() = Icons.Outlined.LightMode
    val DarkMode get() = Icons.Outlined.DarkMode
    val VerifiedUser get() = Icons.Outlined.VerifiedUser
    val Autorenew get() = Icons.Default.Autorenew
    val UnfoldMore get() = Icons.Default.UnfoldMore
    val ExpandLess get() = Icons.Default.ExpandLess
    val ExpandMore get() = Icons.Default.ExpandMore
    val Memory get() = Icons.Default.Memory
    val Notifications get() = Icons.Outlined.Notifications
    val Layers get() = Icons.Outlined.Layers
    val Equalizer get() = Icons.Outlined.Equalizer
    val Lock get() = Icons.Outlined.Lock
    val Title get() = Icons.Outlined.Title
    val TextFields get() = Icons.Outlined.TextFields
    val ArrowDownward get() = Icons.Outlined.ArrowDownward
    val Check get() = Icons.Outlined.Check
}
