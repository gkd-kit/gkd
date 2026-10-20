package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.ui.window.Dialog as PlatformDialog

/** Optional host back dispatcher; Android dialogs already own their native back handling. */
val LocalOverlayBackHandler =
    staticCompositionLocalOf<@Composable (() -> Unit) -> Unit> { { _ -> } }

private data class DialogRegistration(
    val order: Long,
    val registrationOrder: Long,
)

private object DialogRegistry {
    private var nextOrder = 0L
    private var nextRegistrationOrder = 0L
    private val registrations = mutableStateMapOf<Any, DialogRegistration>()

    val topToken: Any?
        get() = registrations.maxWithOrNull(
            compareBy<Map.Entry<Any, DialogRegistration>> { it.value.order }
                .thenBy { it.value.registrationOrder }
        )?.key

    fun createOrder(): Long = ++nextOrder

    fun register(token: Any, order: Long) {
        nextOrder = maxOf(nextOrder, order)
        registrations[token] = DialogRegistration(
            order = order,
            registrationOrder = ++nextRegistrationOrder,
        )
    }

    fun unregister(token: Any) {
        registrations.remove(token)
    }
}

@Composable
private fun DialogLayer(content: @Composable () -> Unit) {
    val order = remember { DialogRegistry.createOrder() }
    val token = remember { Any() }

    DisposableEffect(Unit) {
        DialogRegistry.register(token, order)
        onDispose {
            DialogRegistry.unregister(token)
        }
    }

    if (DialogRegistry.topToken === token) {
        content()
    }
}

@Composable
fun GkDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    DialogLayer {
        if (properties.dismissOnBackPress) LocalOverlayBackHandler.current(onDismissRequest)
        PlatformDialog(
            onDismissRequest = onDismissRequest,
            properties = properties,
            content = content,
        )
    }
}

@Composable
fun GkModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    sheetGesturesEnabled: Boolean = true,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    LocalOverlayBackHandler.current(onDismissRequest)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        sheetMaxWidth = sheetMaxWidth,
        content = content,
    )
}

@Composable
fun GkAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    textToButtonsSpacing: Dp? = null,
) {
    DialogLayer {
        if (properties.dismissOnBackPress) LocalOverlayBackHandler.current(onDismissRequest)
        if (textToButtonsSpacing != null) {
            BasicAlertDialog(
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                properties = properties,
            ) {
                SpacedAlertDialogContent(
                    buttons = {
                        AlertDialogButtons(mainAxisSpacing = 8.dp, crossAxisSpacing = 12.dp) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    },
                    icon = icon,
                    title = title,
                    text = text,
                    shape = shape,
                    containerColor = containerColor,
                    tonalElevation = tonalElevation,
                    buttonContentColor = MaterialTheme.colorScheme.primary,
                    iconContentColor = iconContentColor,
                    titleContentColor = titleContentColor,
                    textContentColor = textContentColor,
                    textToButtonsSpacing = textToButtonsSpacing,
                )
            }
            return@DialogLayer
        }
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties,
        )
    }
}

// Adapted from AndroidX Material3 AlertDialog.kt (Copyright 2021 The Android Open Source
// Project, Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0).
// Keep Material's content styling and wrapping action order; only text padding is configurable.
@Composable
private fun SpacedAlertDialogContent(
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: @Composable (() -> Unit)?,
    shape: Shape,
    containerColor: Color,
    tonalElevation: Dp,
    buttonContentColor: Color,
    iconContentColor: Color,
    titleContentColor: Color,
    textContentColor: Color,
    textToButtonsSpacing: Dp,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        tonalElevation = tonalElevation,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            icon?.let {
                CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                    Box(Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)) {
                        icon()
                    }
                }
            }
            title?.let {
                DialogContentStyle(
                    contentColor = titleContentColor,
                    textStyle = MaterialTheme.typography.headlineSmall,
                ) {
                    Box(
                        // Align the title to the center when an icon is present.
                        Modifier.padding(bottom = 16.dp)
                            .align(
                                if (icon == null) {
                                    Alignment.Start
                                } else {
                                    Alignment.CenterHorizontally
                                }
                            )
                    ) {
                        title()
                    }
                }
            }
            text?.let {
                val textStyle = MaterialTheme.typography.bodyMedium
                DialogContentStyle(
                    contentColor = textContentColor,
                    textStyle = textStyle,
                ) {
                    Box(
                        Modifier.weight(weight = 1f, fill = false)
                            .padding(bottom = textToButtonsSpacing)
                            .align(Alignment.Start)
                    ) {
                        text()
                    }
                }
            }
            Box(modifier = Modifier.align(Alignment.End)) {
                val textStyle = MaterialTheme.typography.labelLarge
                DialogContentStyle(
                    contentColor = buttonContentColor,
                    textStyle = textStyle,
                    content = buttons,
                )
            }
        }
    }
}

/**
 * Simple clone of FlowRow that arranges its children in a horizontal flow with limited
 * customization.
 */
@Composable
private fun AlertDialogButtons(
    mainAxisSpacing: Dp,
    crossAxisSpacing: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content) { measurables, constraints ->
        val sequences = mutableListOf<List<Placeable>>()
        val crossAxisSizes = mutableListOf<Int>()
        val crossAxisPositions = mutableListOf<Int>()

        var mainAxisSpace = 0
        var crossAxisSpace = 0

        val currentSequence = mutableListOf<Placeable>()
        var currentMainAxisSize = 0
        var currentCrossAxisSize = 0

        // Return whether the placeable can be added to the current sequence.
        fun canAddToCurrentSequence(placeable: Placeable) =
            currentSequence.isEmpty() ||
                currentMainAxisSize + mainAxisSpacing.roundToPx() + placeable.width <=
                    constraints.maxWidth

        // Store current sequence information and start a new sequence.
        fun startNewSequence() {
            if (sequences.isNotEmpty()) {
                crossAxisSpace += crossAxisSpacing.roundToPx()
            }
            // Ensures that confirming actions appear above dismissive actions.
            @Suppress("ListIterator") sequences.add(0, currentSequence.toList())
            crossAxisSizes += currentCrossAxisSize
            crossAxisPositions += crossAxisSpace

            crossAxisSpace += currentCrossAxisSize
            mainAxisSpace = max(mainAxisSpace, currentMainAxisSize)

            currentSequence.clear()
            currentMainAxisSize = 0
            currentCrossAxisSize = 0
        }

        measurables.forEach { measurable ->
            // Ask the child for its preferred size.
            val placeable = measurable.measure(constraints)

            // Start a new sequence if there is not enough space.
            if (!canAddToCurrentSequence(placeable)) startNewSequence()

            // Add the child to the current sequence.
            if (currentSequence.isNotEmpty()) {
                currentMainAxisSize += mainAxisSpacing.roundToPx()
            }
            currentSequence.add(placeable)
            currentMainAxisSize += placeable.width
            currentCrossAxisSize = max(currentCrossAxisSize, placeable.height)
        }

        if (currentSequence.isNotEmpty()) startNewSequence()

        val mainAxisLayoutSize = max(mainAxisSpace, constraints.minWidth)

        val crossAxisLayoutSize = max(crossAxisSpace, constraints.minHeight)

        val layoutWidth = mainAxisLayoutSize

        val layoutHeight = crossAxisLayoutSize

        layout(layoutWidth, layoutHeight) {
            sequences.forEachIndexed { i, placeables ->
                val childrenMainAxisSizes =
                    IntArray(placeables.size) { j ->
                        placeables[j].width +
                            if (j < placeables.lastIndex) mainAxisSpacing.roundToPx() else 0
                    }
                val arrangement = Arrangement.End
                val mainAxisPositions = IntArray(childrenMainAxisSizes.size)
                with(arrangement) {
                    arrange(
                        mainAxisLayoutSize,
                        childrenMainAxisSizes,
                        layoutDirection,
                        mainAxisPositions,
                    )
                }
                placeables.forEachIndexed { j, placeable ->
                    placeable.place(x = mainAxisPositions[j], y = crossAxisPositions[i])
                }
            }
        }
    }
}

@Composable
private fun DialogContentStyle(
    contentColor: Color,
    textStyle: androidx.compose.ui.text.TextStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        ProvideTextStyle(textStyle, content)
    }
}
