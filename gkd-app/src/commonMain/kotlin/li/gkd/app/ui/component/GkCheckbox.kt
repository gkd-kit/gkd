package li.gkd.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

@Composable
fun GkCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    key: Any? = null,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) = key(key, MaterialTheme.colorScheme.primary) {
    // Theme colors already animate in GkTheme; reset local color animations as they change.
    val checkmarkColor by animateColorAsState(
        targetValue = if (checked) colors.checkedCheckmarkColor else colors.uncheckedCheckmarkColor,
        animationSpec = tween(180), label = "checkboxCheckmarkColor",
    )
    val boxColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedBoxColor else uncheckedBoxColor
            } else {
                if (checked) disabledCheckedBoxColor else disabledUncheckedBoxColor
            }
        },
        animationSpec = tween(180), label = "checkboxBoxColor",
    )
    val borderColor by animateColorAsState(
        targetValue = with(colors) {
            if (enabled) {
                if (checked) checkedBorderColor else uncheckedBorderColor
            } else {
                if (checked) disabledBorderColor else disabledUncheckedBorderColor
            }
        },
        animationSpec = tween(180), label = "checkboxBorderColor",
    )
    // Share animated values across state slots, including Material's disabled snap path.
    val animatedColors = CheckboxColors(
        checkedCheckmarkColor = checkmarkColor,
        uncheckedCheckmarkColor = checkmarkColor,
        checkedBoxColor = boxColor,
        uncheckedBoxColor = boxColor,
        disabledCheckedBoxColor = boxColor,
        disabledUncheckedBoxColor = boxColor,
        disabledIndeterminateBoxColor = boxColor,
        checkedBorderColor = borderColor,
        uncheckedBorderColor = borderColor,
        disabledBorderColor = borderColor,
        disabledUncheckedBorderColor = borderColor,
        disabledIndeterminateBorderColor = borderColor,
    )
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = animatedColors,
        interactionSource = interactionSource
    )
}
