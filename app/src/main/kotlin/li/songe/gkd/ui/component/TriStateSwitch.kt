package li.songe.gkd.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Dimension constants  (inlined from SwitchTokens / M3 spec)
// ---------------------------------------------------------------------------

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val TrackOutlineWidth = 2.dp
private val ThumbDiameter = 24.dp          // selected handle width
private val UncheckedThumbDiameter = 16.dp        // unselected handle width
private val PressedHandleWidth = 28.dp
private val StateLayerSize = 40.dp
private val ThumbPadding = (TrackHeight - ThumbDiameter) / 2

///** Icon size to use for [thumbContent] */
val TriStateSwitchIconSize = 16.dp

private val DefaultAnimationSpec: FiniteAnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

private val SnapSpecInstance = SnapSpec<Float>()

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * A three-state Material3-style switch.
 *
 * | [checked] value | Visual state  | Thumb position  |
 * |-----------------|---------------|-----------------|
 * | `true`          | ON            | End             |
 * | `null`          | Indeterminate | Center          |
 * | `false`         | OFF           | Start           |
 *
 * @param checked         `true` = on, `false` = off, `null` = indeterminate.
 * @param onCheckedChange Callback when the switch is clicked.  Receives the *next* logical state:
 *                        `false→null`, `null→true`, `true→false` (cycle).
 *                        Pass `null` to make the switch non-interactive.
 * @param modifier        Modifier applied to this composable.
 * @param thumbContent    Optional content drawn inside the thumb (expected size [TriStateSwitchIconSize]).
 * @param enabled         When `false` the switch is non-interactive and visually dimmed.
 * @param colors          [TriStateSwitchColors] controlling the switch appearance.
 * @param interactionSource Optional hoisted [MutableInteractionSource].
 */
@Composable
private fun TriStateSwitch(
    checked: Boolean?,
    onCheckedChange: ((Boolean?) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: TriStateSwitchColors = TriStateSwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

    val toggleableModifier =
        if (onCheckedChange != null) {
            // Cycle: false → null → true → false
            val nextState: Boolean? = when (checked) {
                false -> null
                null -> true
                true -> false
            }
            Modifier
                .minimumInteractiveComponentSize()
                .toggleable(
                    value = checked == true,
                    onValueChange = { onCheckedChange(nextState) },
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = null,
                )
        } else {
            Modifier
        }

    TriStateSwitchImpl(
        modifier = modifier
            .then(toggleableModifier)
            .wrapContentSize(Alignment.Center)
            .requiredSize(TrackWidth, TrackHeight),
        checked = checked,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        thumbShape = MaterialTheme.shapes.extraLarge, // CornerFull equivalent
        thumbContent = thumbContent,
    )
}

// ---------------------------------------------------------------------------
// Internal implementation
// ---------------------------------------------------------------------------

@Composable
@Suppress("ComposableLambdaParameterNaming", "ComposableLambdaParameterPosition")
private fun TriStateSwitchImpl(
    modifier: Modifier,
    checked: Boolean?,
    enabled: Boolean,
    colors: TriStateSwitchColors,
    thumbContent: (@Composable () -> Unit)?,
    interactionSource: InteractionSource,
    thumbShape: Shape,
) {
    val trackColor = colors.trackColor(enabled, checked)
    val thumbColor = colors.thumbColor(enabled, checked)
    val borderColor = colors.borderColor(enabled, checked)
    val trackShape = MaterialTheme.shapes.extraLarge // CornerFull

    Box(
        modifier
            .border(TrackOutlineWidth, borderColor, trackShape)
            .background(trackColor, trackShape)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .then(
                    TriStateThumbElement(
                        interactionSource = interactionSource,
                        checked = checked,
                        animationSpec = DefaultAnimationSpec,
                    )
                )
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false, radius = StateLayerSize / 2),
                )
                .background(thumbColor, thumbShape),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbContent != null) {
                val iconColor = colors.iconColor(enabled, checked)
                CompositionLocalProvider(
                    LocalContentColor provides iconColor,
                    content = thumbContent,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ThumbElement / ThumbNode – layout modifier handling offset + size animation
// ---------------------------------------------------------------------------

private data class TriStateThumbElement(
    val interactionSource: InteractionSource,
    val checked: Boolean?,
    val animationSpec: FiniteAnimationSpec<Float>,
) : ModifierNodeElement<TriStateThumbNode>() {

    override fun create() = TriStateThumbNode(interactionSource, checked, animationSpec)

    override fun update(node: TriStateThumbNode) {
        node.interactionSource = interactionSource
        if (node.checked != checked) node.invalidateMeasurement()
        node.checked = checked
        node.animationSpec = animationSpec
        node.update()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "triStateSwitchThumb"
        properties["interactionSource"] = interactionSource
        properties["checked"] = checked
        properties["animationSpec"] = animationSpec
    }
}

private class TriStateThumbNode(
    var interactionSource: InteractionSource,
    var checked: Boolean?,
    var animationSpec: FiniteAnimationSpec<Float>,
) : Modifier.Node(), LayoutModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private var isPressed = false
    private var offsetAnim: Animatable<Float, AnimationVector1D>? = null
    private var sizeAnim: Animatable<Float, AnimationVector1D>? = null
    private var initialOffset: Float = Float.NaN
    private var initialSize: Float = Float.NaN

    override fun onAttach() {
        coroutineScope.launch {
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release -> pressCount--
                    is PressInteraction.Cancel -> pressCount--
                }
                val pressed = pressCount > 0
                if (isPressed != pressed) {
                    isPressed = pressed
                    invalidateMeasurement()
                }
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val hasContent =
            measurable.maxIntrinsicHeight(constraints.maxWidth) != 0 &&
                    measurable.maxIntrinsicWidth(constraints.maxHeight) != 0

        // Target thumb size
        val size = when {
            isPressed -> PressedHandleWidth.toPx()
            hasContent || checked == true -> ThumbDiameter.toPx()
            checked == null -> androidx.compose.ui.util.lerp(
                UncheckedThumbDiameter.toPx(),
                ThumbDiameter.toPx(),
                0.5f
            )

            else /* false */ -> UncheckedThumbDiameter.toPx()
        }

        val actualSize = (sizeAnim?.value ?: size).toInt()
        val placeable = measurable.measure(Constraints.fixed(actualSize, actualSize))

        // Offset bounds
        val thumbPaddingStart = (TrackHeight - size.toDp()) / 2f
        val minBound = thumbPaddingStart.toPx()
        val thumbPathLength = (TrackWidth - ThumbDiameter) - ThumbPadding
        val maxBound = thumbPathLength.toPx()
        val midBound = (minBound + maxBound) / 2f

        val offset = when {
            isPressed && checked == true -> maxBound - TrackOutlineWidth.toPx()
            isPressed && checked == false -> TrackOutlineWidth.toPx()
            isPressed && checked == null -> midBound
            checked == true -> maxBound
            checked == null -> midBound
            else -> minBound
        }

        // Animate size
        if (sizeAnim?.targetValue != size) {
            coroutineScope.launch {
                sizeAnim?.animateTo(size, if (isPressed) SnapSpecInstance else animationSpec)
            }
        }
        // Animate offset
        if (offsetAnim?.targetValue != offset) {
            coroutineScope.launch {
                offsetAnim?.animateTo(offset, if (isPressed) SnapSpecInstance else animationSpec)
            }
        }

        if (initialSize.isNaN() && initialOffset.isNaN()) {
            initialSize = size
            initialOffset = offset
        }

        return layout(actualSize, actualSize) {
            placeable.placeRelative(offsetAnim?.value?.toInt() ?: offset.toInt(), 0)
        }
    }

    fun update() {
        if (sizeAnim == null && !initialSize.isNaN()) {
            sizeAnim = Animatable(initialSize)
        }
        if (offsetAnim == null && !initialOffset.isNaN()) {
            offsetAnim = Animatable(initialOffset)
        }
    }
}

// ---------------------------------------------------------------------------
// Colors
// ---------------------------------------------------------------------------

/** Default factory object for [TriStateSwitchColors]. */
object TriStateSwitchDefaults {

    /**
     * Creates a [TriStateSwitchColors] using Material3 color-scheme values.
     *
     * The **indeterminate** colors are derived by linearly interpolating (50 %) between the
     * checked and unchecked colors so the visual midpoint looks natural.
     *
     * @param checkedThumbColor              Thumb color when enabled + checked.
     * @param checkedTrackColor              Track color when enabled + checked.
     * @param checkedBorderColor             Border color when enabled + checked.
     * @param checkedIconColor               Icon color when enabled + checked.
     * @param uncheckedThumbColor            Thumb color when enabled + unchecked.
     * @param uncheckedTrackColor            Track color when enabled + unchecked.
     * @param uncheckedBorderColor           Border color when enabled + unchecked.
     * @param uncheckedIconColor             Icon color when enabled + unchecked.
     * @param indeterminateThumbColor        Thumb color when enabled + indeterminate (`null`).
     * @param indeterminateTrackColor        Track color when enabled + indeterminate.
     * @param indeterminateBorderColor       Border color when enabled + indeterminate.
     * @param indeterminateIconColor         Icon color when enabled + indeterminate.
     * @param disabledCheckedThumbColor      Thumb color when disabled + checked.
     * @param disabledCheckedTrackColor      Track color when disabled + checked.
     * @param disabledCheckedBorderColor     Border color when disabled + checked.
     * @param disabledCheckedIconColor       Icon color when disabled + checked.
     * @param disabledUncheckedThumbColor    Thumb color when disabled + unchecked.
     * @param disabledUncheckedTrackColor    Track color when disabled + unchecked.
     * @param disabledUncheckedBorderColor   Border color when disabled + unchecked.
     * @param disabledUncheckedIconColor     Icon color when disabled + unchecked.
     * @param disabledIndeterminateThumbColor  Thumb color when disabled + indeterminate.
     * @param disabledIndeterminateTrackColor  Track color when disabled + indeterminate.
     * @param disabledIndeterminateBorderColor Border color when disabled + indeterminate.
     * @param disabledIndeterminateIconColor   Icon color when disabled + indeterminate.
     */
    @Composable
    fun colors(
        // --- enabled checked ---
        checkedThumbColor: Color = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor: Color = MaterialTheme.colorScheme.primary,
        checkedBorderColor: Color = Color.Transparent,
        checkedIconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
        // --- enabled unchecked ---
        uncheckedThumbColor: Color = MaterialTheme.colorScheme.outline,
        uncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        uncheckedBorderColor: Color = MaterialTheme.colorScheme.outline,
        uncheckedIconColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        // --- enabled indeterminate (null) — default = midpoint blend ---
        indeterminateThumbColor: Color = lerp(uncheckedThumbColor, checkedThumbColor, 0.5f),
        indeterminateTrackColor: Color = lerp(uncheckedTrackColor, checkedTrackColor, 0.5f),
        indeterminateBorderColor: Color = lerp(uncheckedBorderColor, checkedBorderColor, 0.5f),
        indeterminateIconColor: Color = lerp(uncheckedIconColor, checkedIconColor, 0.5f),
        // --- disabled checked ---
        disabledCheckedThumbColor: Color =
            MaterialTheme.colorScheme.surface
                .copy(alpha = 1.0f)
                .compositeOver(MaterialTheme.colorScheme.surface),
        disabledCheckedTrackColor: Color =
            MaterialTheme.colorScheme.onSurface
                .copy(alpha = 0.12f)
                .compositeOver(MaterialTheme.colorScheme.surface),
        disabledCheckedBorderColor: Color = Color.Transparent,
        disabledCheckedIconColor: Color =
            MaterialTheme.colorScheme.onSurface
                .copy(alpha = 0.38f)
                .compositeOver(MaterialTheme.colorScheme.surface),
        // --- disabled unchecked ---
        disabledUncheckedThumbColor: Color =
            MaterialTheme.colorScheme.onSurface
                .copy(alpha = 0.38f)
                .compositeOver(MaterialTheme.colorScheme.surface),
        disabledUncheckedTrackColor: Color =
            MaterialTheme.colorScheme.surfaceContainerHighest
                .copy(alpha = 0.12f)
                .compositeOver(MaterialTheme.colorScheme.surface),
        disabledUncheckedBorderColor: Color =
            MaterialTheme.colorScheme.onSurface
                .copy(alpha = 0.12f)
                .compositeOver(MaterialTheme.colorScheme.surface),
        disabledUncheckedIconColor: Color =
            MaterialTheme.colorScheme.surfaceContainerHighest
                .copy(alpha = 0.38f)
                .compositeOver(MaterialTheme.colorScheme.surface),
        // --- disabled indeterminate ---
        disabledIndeterminateThumbColor: Color = lerp(
            disabledUncheckedThumbColor,
            disabledCheckedThumbColor,
            0.5f
        ),
        disabledIndeterminateTrackColor: Color = lerp(
            disabledUncheckedTrackColor,
            disabledCheckedTrackColor,
            0.5f
        ),
        disabledIndeterminateBorderColor: Color = lerp(
            disabledUncheckedBorderColor,
            disabledCheckedBorderColor,
            0.5f
        ),
        disabledIndeterminateIconColor: Color = lerp(
            disabledUncheckedIconColor,
            disabledCheckedIconColor,
            0.5f
        ),
    ): TriStateSwitchColors = TriStateSwitchColors(
        checkedThumbColor = checkedThumbColor,
        checkedTrackColor = checkedTrackColor,
        checkedBorderColor = checkedBorderColor,
        checkedIconColor = checkedIconColor,
        uncheckedThumbColor = uncheckedThumbColor,
        uncheckedTrackColor = uncheckedTrackColor,
        uncheckedBorderColor = uncheckedBorderColor,
        uncheckedIconColor = uncheckedIconColor,
        indeterminateThumbColor = indeterminateThumbColor,
        indeterminateTrackColor = indeterminateTrackColor,
        indeterminateBorderColor = indeterminateBorderColor,
        indeterminateIconColor = indeterminateIconColor,
        disabledCheckedThumbColor = disabledCheckedThumbColor,
        disabledCheckedTrackColor = disabledCheckedTrackColor,
        disabledCheckedBorderColor = disabledCheckedBorderColor,
        disabledCheckedIconColor = disabledCheckedIconColor,
        disabledUncheckedThumbColor = disabledUncheckedThumbColor,
        disabledUncheckedTrackColor = disabledUncheckedTrackColor,
        disabledUncheckedBorderColor = disabledUncheckedBorderColor,
        disabledUncheckedIconColor = disabledUncheckedIconColor,
        disabledIndeterminateThumbColor = disabledIndeterminateThumbColor,
        disabledIndeterminateTrackColor = disabledIndeterminateTrackColor,
        disabledIndeterminateBorderColor = disabledIndeterminateBorderColor,
        disabledIndeterminateIconColor = disabledIndeterminateIconColor,
    )

    /** Icon size to use for `thumbContent`. */
//    val IconSize = TriStateSwitchIconSize
}

/**
 * Represents the colors used by a [TriStateSwitch] in all states.
 *
 * Use [TriStateSwitchDefaults.colors] to obtain the default Material3 instance.
 */
@Immutable
class TriStateSwitchColors(
    val checkedThumbColor: Color,
    val checkedTrackColor: Color,
    val checkedBorderColor: Color,
    val checkedIconColor: Color,
    val uncheckedThumbColor: Color,
    val uncheckedTrackColor: Color,
    val uncheckedBorderColor: Color,
    val uncheckedIconColor: Color,
    val indeterminateThumbColor: Color,
    val indeterminateTrackColor: Color,
    val indeterminateBorderColor: Color,
    val indeterminateIconColor: Color,
    val disabledCheckedThumbColor: Color,
    val disabledCheckedTrackColor: Color,
    val disabledCheckedBorderColor: Color,
    val disabledCheckedIconColor: Color,
    val disabledUncheckedThumbColor: Color,
    val disabledUncheckedTrackColor: Color,
    val disabledUncheckedBorderColor: Color,
    val disabledUncheckedIconColor: Color,
    val disabledIndeterminateThumbColor: Color,
    val disabledIndeterminateTrackColor: Color,
    val disabledIndeterminateBorderColor: Color,
    val disabledIndeterminateIconColor: Color,
) {
    /** Returns a copy overriding only the supplied parameters (Color.Unspecified = keep original). */
    fun copy(
        checkedThumbColor: Color = this.checkedThumbColor,
        checkedTrackColor: Color = this.checkedTrackColor,
        checkedBorderColor: Color = this.checkedBorderColor,
        checkedIconColor: Color = this.checkedIconColor,
        uncheckedThumbColor: Color = this.uncheckedThumbColor,
        uncheckedTrackColor: Color = this.uncheckedTrackColor,
        uncheckedBorderColor: Color = this.uncheckedBorderColor,
        uncheckedIconColor: Color = this.uncheckedIconColor,
        indeterminateThumbColor: Color = this.indeterminateThumbColor,
        indeterminateTrackColor: Color = this.indeterminateTrackColor,
        indeterminateBorderColor: Color = this.indeterminateBorderColor,
        indeterminateIconColor: Color = this.indeterminateIconColor,
        disabledCheckedThumbColor: Color = this.disabledCheckedThumbColor,
        disabledCheckedTrackColor: Color = this.disabledCheckedTrackColor,
        disabledCheckedBorderColor: Color = this.disabledCheckedBorderColor,
        disabledCheckedIconColor: Color = this.disabledCheckedIconColor,
        disabledUncheckedThumbColor: Color = this.disabledUncheckedThumbColor,
        disabledUncheckedTrackColor: Color = this.disabledUncheckedTrackColor,
        disabledUncheckedBorderColor: Color = this.disabledUncheckedBorderColor,
        disabledUncheckedIconColor: Color = this.disabledUncheckedIconColor,
        disabledIndeterminateThumbColor: Color = this.disabledIndeterminateThumbColor,
        disabledIndeterminateTrackColor: Color = this.disabledIndeterminateTrackColor,
        disabledIndeterminateBorderColor: Color = this.disabledIndeterminateBorderColor,
        disabledIndeterminateIconColor: Color = this.disabledIndeterminateIconColor,
    ) = TriStateSwitchColors(
        checkedThumbColor = checkedThumbColor,
        checkedTrackColor = checkedTrackColor,
        checkedBorderColor = checkedBorderColor,
        checkedIconColor = checkedIconColor,
        uncheckedThumbColor = uncheckedThumbColor,
        uncheckedTrackColor = uncheckedTrackColor,
        uncheckedBorderColor = uncheckedBorderColor,
        uncheckedIconColor = uncheckedIconColor,
        indeterminateThumbColor = indeterminateThumbColor,
        indeterminateTrackColor = indeterminateTrackColor,
        indeterminateBorderColor = indeterminateBorderColor,
        indeterminateIconColor = indeterminateIconColor,
        disabledCheckedThumbColor = disabledCheckedThumbColor,
        disabledCheckedTrackColor = disabledCheckedTrackColor,
        disabledCheckedBorderColor = disabledCheckedBorderColor,
        disabledCheckedIconColor = disabledCheckedIconColor,
        disabledUncheckedThumbColor = disabledUncheckedThumbColor,
        disabledUncheckedTrackColor = disabledUncheckedTrackColor,
        disabledUncheckedBorderColor = disabledUncheckedBorderColor,
        disabledUncheckedIconColor = disabledUncheckedIconColor,
        disabledIndeterminateThumbColor = disabledIndeterminateThumbColor,
        disabledIndeterminateTrackColor = disabledIndeterminateTrackColor,
        disabledIndeterminateBorderColor = disabledIndeterminateBorderColor,
        disabledIndeterminateIconColor = disabledIndeterminateIconColor,
    )

    @Stable
    internal fun thumbColor(enabled: Boolean, checked: Boolean?): Color =
        if (enabled) when (checked) {
            true -> checkedThumbColor
            false -> uncheckedThumbColor
            null -> indeterminateThumbColor
        } else when (checked) {
            true -> disabledCheckedThumbColor
            false -> disabledUncheckedThumbColor
            null -> disabledIndeterminateThumbColor
        }

    @Stable
    internal fun trackColor(enabled: Boolean, checked: Boolean?): Color =
        if (enabled) when (checked) {
            true -> checkedTrackColor
            false -> uncheckedTrackColor
            null -> indeterminateTrackColor
        } else when (checked) {
            true -> disabledCheckedTrackColor
            false -> disabledUncheckedTrackColor
            null -> disabledIndeterminateTrackColor
        }

    @Stable
    internal fun borderColor(enabled: Boolean, checked: Boolean?): Color =
        if (enabled) when (checked) {
            true -> checkedBorderColor
            false -> uncheckedBorderColor
            null -> indeterminateBorderColor
        } else when (checked) {
            true -> disabledCheckedBorderColor
            false -> disabledUncheckedBorderColor
            null -> disabledIndeterminateBorderColor
        }

    @Stable
    internal fun iconColor(enabled: Boolean, checked: Boolean?): Color =
        if (enabled) when (checked) {
            true -> checkedIconColor
            false -> uncheckedIconColor
            null -> indeterminateIconColor
        } else when (checked) {
            true -> disabledCheckedIconColor
            false -> disabledUncheckedIconColor
            null -> disabledIndeterminateIconColor
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TriStateSwitchColors) return false
        return checkedThumbColor == other.checkedThumbColor &&
                checkedTrackColor == other.checkedTrackColor &&
                checkedBorderColor == other.checkedBorderColor &&
                checkedIconColor == other.checkedIconColor &&
                uncheckedThumbColor == other.uncheckedThumbColor &&
                uncheckedTrackColor == other.uncheckedTrackColor &&
                uncheckedBorderColor == other.uncheckedBorderColor &&
                uncheckedIconColor == other.uncheckedIconColor &&
                indeterminateThumbColor == other.indeterminateThumbColor &&
                indeterminateTrackColor == other.indeterminateTrackColor &&
                indeterminateBorderColor == other.indeterminateBorderColor &&
                indeterminateIconColor == other.indeterminateIconColor &&
                disabledCheckedThumbColor == other.disabledCheckedThumbColor &&
                disabledCheckedTrackColor == other.disabledCheckedTrackColor &&
                disabledCheckedBorderColor == other.disabledCheckedBorderColor &&
                disabledCheckedIconColor == other.disabledCheckedIconColor &&
                disabledUncheckedThumbColor == other.disabledUncheckedThumbColor &&
                disabledUncheckedTrackColor == other.disabledUncheckedTrackColor &&
                disabledUncheckedBorderColor == other.disabledUncheckedBorderColor &&
                disabledUncheckedIconColor == other.disabledUncheckedIconColor &&
                disabledIndeterminateThumbColor == other.disabledIndeterminateThumbColor &&
                disabledIndeterminateTrackColor == other.disabledIndeterminateTrackColor &&
                disabledIndeterminateBorderColor == other.disabledIndeterminateBorderColor &&
                disabledIndeterminateIconColor == other.disabledIndeterminateIconColor
    }

    override fun hashCode(): Int {
        var r = checkedThumbColor.hashCode()
        r = 31 * r + checkedTrackColor.hashCode()
        r = 31 * r + checkedBorderColor.hashCode()
        r = 31 * r + checkedIconColor.hashCode()
        r = 31 * r + uncheckedThumbColor.hashCode()
        r = 31 * r + uncheckedTrackColor.hashCode()
        r = 31 * r + uncheckedBorderColor.hashCode()
        r = 31 * r + uncheckedIconColor.hashCode()
        r = 31 * r + indeterminateThumbColor.hashCode()
        r = 31 * r + indeterminateTrackColor.hashCode()
        r = 31 * r + indeterminateBorderColor.hashCode()
        r = 31 * r + indeterminateIconColor.hashCode()
        r = 31 * r + disabledCheckedThumbColor.hashCode()
        r = 31 * r + disabledCheckedTrackColor.hashCode()
        r = 31 * r + disabledCheckedBorderColor.hashCode()
        r = 31 * r + disabledCheckedIconColor.hashCode()
        r = 31 * r + disabledUncheckedThumbColor.hashCode()
        r = 31 * r + disabledUncheckedTrackColor.hashCode()
        r = 31 * r + disabledUncheckedBorderColor.hashCode()
        r = 31 * r + disabledUncheckedIconColor.hashCode()
        r = 31 * r + disabledIndeterminateThumbColor.hashCode()
        r = 31 * r + disabledIndeterminateTrackColor.hashCode()
        r = 31 * r + disabledIndeterminateBorderColor.hashCode()
        r = 31 * r + disabledIndeterminateIconColor.hashCode()
        return r
    }
}

@Composable
fun PerfTriStateSwitch(
    checked: Boolean?,
    onCheckedChange: ((Boolean?) -> Unit)?,
    modifier: Modifier = Modifier,
    key: Any? = null,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: TriStateSwitchColors = TriStateSwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) = androidx.compose.runtime.key(key) {
    TriStateSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewTriStateSwitch() {
    Column {
        PerfTriStateSwitch(
            checked = false,
            onCheckedChange = {},
        )
        PerfTriStateSwitch(
            checked = null,
            onCheckedChange = {},
        )
        PerfTriStateSwitch(
            checked = true,
            onCheckedChange = {},
        )
    }
}
