/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material3.pullrefresh

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.Drag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.platform.inspectable
import androidx.compose.ui.unit.Velocity

/**
 * A nested scroll modifier that provides scroll events to [state].
 *
 * Note that this modifier must be added above a scrolling container, such as a lazy column, in
 * order to receive scroll events. For example:
 *
 * @sample androidx.compose.material.samples.PullRefreshSample
 *
 * @param state The [PullRefreshState] associated with this pull-to-refresh component.
 * The state will be updated by this modifier.
 * @param enabled If not enabled, all scroll delta and fling velocity will be ignored.
 */
// TODO(b/244423199): Move pullRefresh into its own material library similar to material-ripple.
fun Modifier.pullRefresh(
    state: PullRefreshState,
    enabled: Boolean = true,
) = inspectable(
    inspectorInfo = debugInspectorInfo {
        name = "pullRefresh"
        properties["state"] = state
        properties["enabled"] = enabled
    },
) {
    Modifier.pullRefresh(state::onPull, state::onRelease, enabled)
}

/**
 * A nested scroll modifier that provides [onPull] and [onRelease] callbacks to aid building custom
 * pull refresh components.
 *
 * Note that this modifier must be added above a scrolling container, such as a lazy column, in
 * order to receive scroll events. For example:
 *
 * @sample androidx.compose.material.samples.CustomPullRefreshSample
 *
 * @param onPull Callback for dispatching vertical scroll delta, takes float pullDelta as argument.
 * Positive delta (pulling down) is dispatched only if the child does not consume it (i.e. pulling
 * down despite being at the top of a scrollable component), whereas negative delta (swiping up) is
 * dispatched first (in case it is needed to push the indicator back up), and then the unconsumed
 * delta is passed on to the child. The callback returns how much delta was consumed.
 * @param onRelease Callback for when drag is released, takes float flingVelocity as argument.
 * The callback returns how much velocity was consumed - in most cases this should only consume
 * velocity if pull refresh has been dragged already and the velocity is positive (the fling is
 * downwards), as an upwards fling should typically still scroll a scrollable component beneath the
 * pullRefresh. This is invoked before any remaining velocity is passed to the child.
 * @param enabled If not enabled, all scroll delta and fling velocity will be ignored and neither
 * [onPull] nor [onRelease] will be invoked.
 */
fun Modifier.pullRefresh(
    onPull: (pullDelta: Float) -> Float,
    onRelease: suspend (flingVelocity: Float) -> Float,
    enabled: Boolean = true,
) = inspectable(
    inspectorInfo = debugInspectorInfo {
        name = "pullRefresh"
        properties["onPull"] = onPull
        properties["onRelease"] = onRelease
        properties["enabled"] = enabled
    },
) {
    Modifier.nestedScroll(PullRefreshNestedScrollConnection(onPull, onRelease, enabled))
}

private class PullRefreshNestedScrollConnection(
    private val onPull: (pullDelta: Float) -> Float,
    private val onRelease: suspend (flingVelocity: Float) -> Float,
    private val enabled: Boolean,
) : NestedScrollConnection {

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset = when {
        !enabled -> Offset.Zero
        source == Drag && available.y < 0 -> Offset(0f, onPull(available.y)) // Swiping up
        else -> Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = when {
        !enabled -> Offset.Zero
        source == Drag && available.y > 0 -> Offset(0f, onPull(available.y)) // Pulling down
        else -> Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return Velocity(0f, onRelease(available.y))
    }
}
