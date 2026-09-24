package li.gkd.app.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.ui.icon.GkBackCloseIcon
import li.gkd.app.ui.share.noRippleClickable

private val ActionRowHeight = 48.dp
private val BarEdgePadding = 4.dp
private val TitleStartPadding = 12.dp

@Composable
fun GkMultiSelectionTopAppBar(
    selectedMode: Boolean,
    selectedCount: Int,
    onExitSelection: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    canScroll: Boolean = true,
    actions: @Composable RowScope.(selectedMode: Boolean) -> Unit = {},
) {
    val transition = updateTransition(selectedMode, label = "multiSelectionTopBar")
    var lastSelectedCount by remember { mutableIntStateOf(selectedCount) }
    SideEffect {
        if (selectedMode) lastSelectedCount = selectedCount
    }
    // Clearing selection must not replace the outgoing title with "0 items selected".
    val displayedCount = if (selectedMode) selectedCount else lastSelectedCount
    val density = LocalDensity.current
    val travel = with(density) { ActionRowHeight.roundToPx() }
    var normalTitleHeight by remember(density) { mutableIntStateOf(0) }
    val colors = TopAppBarDefaults.topAppBarColors()

    val animatedTitle: @Composable () -> Unit = {
        transition.AnimatedContent(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = with(density) { normalTitleHeight.toDp() }),
            transitionSpec = { selectionTransform(travel) },
            contentAlignment = Alignment.CenterStart,
        ) { contentSelectedMode ->
            val currentContent = contentSelectedMode == selectedMode
            // Each state owns its complete layout. Adding the close button cannot
            // change the horizontal position of either title during the transition.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionRowHeight)
                    .focusProperties { canFocus = currentContent }
                    .semantics { if (!currentContent) hideFromAccessibility() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onNavigateBack == null && contentSelectedMode) {
                    CompositionLocalProvider(LocalContentColor provides colors.navigationIconContentColor) {
                        GkIconButton(
                            imageVector = GkIcons.Close,
                            contentDescription = UiStrings.selection_cancel,
                            onClick = onExitSelection,
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(1f)
                        .then(
                            if (onNavigateBack == null) {
                                Modifier.padding(
                                    start = if (contentSelectedMode) BarEdgePadding else TitleStartPadding,
                                    end = BarEdgePadding,
                                )
                            } else Modifier,
                        )
                        .then(
                            if (onTitleClick != null) {
                                Modifier.noRippleClickable(onClick = onTitleClick)
                            } else Modifier,
                        )
                        .then(
                            if (!contentSelectedMode) {
                                // Retain the normal two-line title's height at large font scales.
                                Modifier.onSizeChanged { normalTitleHeight = it.height }
                            } else Modifier,
                        ),
                ) {
                    if (contentSelectedMode) {
                        Text(UiStrings.selection_count(displayedCount), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        title()
                    }
                }
            }
        }
    }
    val animatedActions: @Composable RowScope.() -> Unit = {
        transition.AnimatedContent(
            transitionSpec = { selectionTransform(travel) },
            contentAlignment = Alignment.CenterEnd,
        ) { contentSelectedMode ->
            Row(
                modifier = Modifier.heightIn(min = ActionRowHeight)
                    .focusProperties { canFocus = contentSelectedMode == selectedMode }
                    .semantics { if (contentSelectedMode != selectedMode) hideFromAccessibility() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions(contentSelectedMode)
            }
        }
    }

    if (onNavigateBack != null) {
        GkTopAppBar(
            modifier = modifier,
            scrollBehavior = scrollBehavior,
            canScroll = canScroll,
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (selectedMode) onExitSelection() else onNavigateBack()
                    },
                ) {
                    GkBackCloseIcon(backOrClose = !selectedMode)
                }
            },
            title = animatedTitle,
            actions = animatedActions,
        )
    } else {
        PinnedSelectionTopAppBar(modifier, scrollBehavior, canScroll) {
            CompositionLocalProvider(LocalContentColor provides colors.titleContentColor) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                    Box(Modifier.weight(1f)) { animatedTitle() }
                }
            }
            CompositionLocalProvider(LocalContentColor provides colors.actionIconContentColor) {
                animatedActions()
            }
        }
    }
}

// Use one distance as well as one Transition: text height and icon height can differ.
private fun AnimatedContentTransitionScope<Boolean>.selectionTransform(travel: Int) =
    if (targetState) {
        (slideInVertically { travel } + fadeIn() togetherWith
            slideOutVertically { -travel } + fadeOut()).using(null)
    } else {
        (slideInVertically { -travel } + fadeIn() togetherWith
            slideOutVertically { travel } + fadeOut()).using(null)
    }

@Composable
private fun PinnedSelectionTopAppBar(
    modifier: Modifier,
    scrollBehavior: TopAppBarScrollBehavior?,
    canScroll: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    // The subscription tab is pinned. Material3 still owns its background, insets and
    // scroll color; a single overlaid row lets its leading button and title share a slot.
    // Measure that row first, including large text, without a size -> state -> layout loop.
    val insets = (LocalActivity.current as MainActivity).topBarWindowInsets
    Layout(
        modifier = modifier.semantics { isTraversalGroup = true },
        contents = listOf(
            { GkTopAppBar(title = {}, scrollBehavior = scrollBehavior, canScroll = canScroll) },
            {
                Box(Modifier.windowInsetsPadding(insets).clipToBounds()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = TopAppBarDefaults.TopAppBarExpandedHeight)
                            .padding(horizontal = BarEdgePadding),
                        verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            },
        ),
    ) { (background, foreground), constraints ->
        val row = foreground.single().measure(constraints)
        val bar = background.single().measure(Constraints.fixed(row.width, row.height))
        layout(row.width, row.height) {
            bar.placeRelative(0, 0)
            row.placeRelative(0, 0)
        }
    }
}
