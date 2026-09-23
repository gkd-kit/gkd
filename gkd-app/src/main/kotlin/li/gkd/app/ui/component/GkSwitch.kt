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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import li.gkd.app.text.UiStrings

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
) = androidx.compose.runtime.key(key, MaterialTheme.colorScheme.primary) {
    val thumbColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedThumbColor else uncheckedThumbColor
            } else {
                if (checked) disabledCheckedThumbColor else disabledUncheckedThumbColor
            }
        }, animationSpec = tween(180), label = "switchThumbColor",
    )
    val trackColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedTrackColor else uncheckedTrackColor
            } else {
                if (checked) disabledCheckedTrackColor else disabledUncheckedTrackColor
            }
        }, animationSpec = tween(180), label = "switchTrackColor",
    )
    val borderColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedBorderColor else uncheckedBorderColor
            } else {
                if (checked) disabledCheckedBorderColor else disabledUncheckedBorderColor
            }
        }, animationSpec = tween(180), label = "switchBorderColor",
    )
    val iconColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedIconColor else uncheckedIconColor
            } else {
                if (checked) disabledCheckedIconColor else disabledUncheckedIconColor
            }
        }, animationSpec = tween(180), label = "switchIconColor",
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
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.semantics {
            stateDescription = if (checked) UiStrings.turned_on else UiStrings.turned_off
        },
        thumbContent = thumbContent,
        enabled = enabled,
        colors = animatedColors,
        interactionSource = interactionSource,
    )
}
