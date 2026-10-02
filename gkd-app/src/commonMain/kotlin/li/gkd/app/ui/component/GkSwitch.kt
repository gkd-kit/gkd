package li.gkd.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import li.gkd.app.resources.Res
import li.gkd.app.resources.turned_off
import li.gkd.app.resources.turned_on
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    key: Any? = null,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) = key(key, MaterialTheme.colorScheme.primary) {
    // Theme colors already animate in GkTheme. Reset internal animation state as they
    // change so Switch does not add a second color transition on top of that animation.
    // The shared theme projection defers theme updates until after the ordinary toggle transition.
    val thumbColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedThumbColor else uncheckedThumbColor
            } else {
                if (checked) disabledCheckedThumbColor else disabledUncheckedThumbColor
            }
        },
        animationSpec = tween(180), label = "switchThumbColor",
    )
    val trackColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedTrackColor else uncheckedTrackColor
            } else {
                if (checked) disabledCheckedTrackColor else disabledUncheckedTrackColor
            }
        },
        animationSpec = tween(180), label = "switchTrackColor",
    )
    val borderColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedBorderColor else uncheckedBorderColor
            } else {
                if (checked) disabledCheckedBorderColor else disabledUncheckedBorderColor
            }
        },
        animationSpec = tween(180), label = "switchBorderColor",
    )
    val iconColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedIconColor else uncheckedIconColor
            } else {
                if (checked) disabledCheckedIconColor else disabledUncheckedIconColor
            }
        },
        animationSpec = tween(180), label = "switchIconColor",
    )
    // Feed every state slot the animated value so an enabled/checked change cannot
    // switch Material's color lookup to an unanimated slot mid-transition.
    val animatedColors = SwitchColors(
        checkedThumbColor = thumbColor,
        checkedTrackColor = trackColor,
        checkedBorderColor = borderColor,
        checkedIconColor = iconColor,
        uncheckedThumbColor = thumbColor,
        uncheckedTrackColor = trackColor,
        uncheckedBorderColor = borderColor,
        uncheckedIconColor = iconColor,
        disabledCheckedThumbColor = thumbColor,
        disabledCheckedTrackColor = trackColor,
        disabledCheckedBorderColor = borderColor,
        disabledCheckedIconColor = iconColor,
        disabledUncheckedThumbColor = thumbColor,
        disabledUncheckedTrackColor = trackColor,
        disabledUncheckedBorderColor = borderColor,
        disabledUncheckedIconColor = iconColor,
    )
    val description =
        if (checked) stringResource(Res.string.turned_on) else stringResource(Res.string.turned_off)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.semantics {
            stateDescription = description
        },
        thumbContent = thumbContent,
        enabled = enabled,
        colors = animatedColors,
        interactionSource = interactionSource,
    )
}
