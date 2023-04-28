package li.songe.router

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.blankj.utilcode.util.ScreenUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.ArrayDeque

@Composable
fun RouterHost(startPage: Page) {
    val screenWidth = remember {
        ScreenUtils.getScreenWidth()
    }
    val scope = rememberCoroutineScope()

    var history by remember {
        mutableStateOf(
            listOf(
                Route(startPage, null) {} to Animatable(
                    0,
                    Int.VectorConverter
                )
            )
        )
    }
    val tasks = remember {
        ArrayDeque<Deferred<*>>()
    }

    val router = remember {
        Router({ newRoute ->
            tasks.add(scope.async {
                val newAnim = Animatable(
                    screenWidth,
                    Int.VectorConverter
                )
                val lastAnim = history.last().second
                history = history.toMutableList().apply {
                    add(newRoute to newAnim)
                }
                val t1 = scope.launch {
                    newAnim.animateTo(
                        0,
                        TweenSpec(
                            pageTransitionDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                val t2 = scope.launch {
                    lastAnim.animateTo(
                        screenWidth / -3,
                        TweenSpec(
                            pageTransitionDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                t1.join()
                t2.join()
            })
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().await()
            }
        }, { result ->
            tasks.add(scope.async {
                val currentAnim = history.last().second
                val currentRoute = history.last().first
                val lastAnim = history[history.size - 2].second
                val t1 = scope.launch {
                    currentAnim.animateTo(
                        screenWidth,
                        TweenSpec(
                            pageTransitionDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                val t2 = scope.launch {
                    lastAnim.animateTo(
                        0,
                        TweenSpec(
                            pageTransitionDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                t1.join()
                t2.join()
                currentRoute.onBack(result)
                history = history.toMutableList().apply { removeLast() }
            })
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().await()
            }
        }, scope)
    }

    BackHandler(history.size > 1) {
        router.back()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        history.forEachIndexed { index, pair ->
            key(index) {
                CompositionLocalProvider(LocalRouter provides router) {
                    val route = pair.first
                    val anim = pair.second
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .graphicsLayer {
                                translationX = anim.value.toFloat()
                            }
                            .background(Color.White)
                    ) {
                        key(Unit) {
                            CompositionLocalProvider(
                                LocalRoute provides route,
                                LocalRouteShow provides (route == history.last().first)
                            ) {
                                route.page()
                            }
                        }
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 0.5f * (anim.value / (screenWidth / -3f))
                            }
                            .background(Color.Black)
                        )
                    }
                }
            }
        }
    }
}








