package li.songe.gkd.router

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.router.Router.Companion.LocalRouter
import li.songe.gkd.use.UseHook.screenWidth
import li.songe.gkd.util.ModifierExt.noRippleClickable

@Composable
fun <P> RouterHost(
    startPage: Page<P, *>,
    params: P = startPage.defaultParams
//    builder: (accept:(Page<*, *>) -> Unit) -> Unit

) {
    val activity = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
//    1:pushing, -1:backing
    var direction by remember { mutableStateOf(0) }
    var stack: List<Pair<Page<*, *>, *>> by remember { mutableStateOf(listOf(startPage to params)) }
    val router = remember {
        Router<Any?>({
            stack
        }, {
//            如果正在运行动画, 则取取消路由变换
            if (direction != 0) return@Router

            if (it.isEmpty()) {
                activity.finish()
                return@Router
            }

            direction = if (it.size > stack.size) {
                1
//                    push
            } else {
                -1
//                    back
            }

            scope.launch {
//                后退动作
                if (direction == -1) {
                    delay(pageTransitionDurationMillis.toLong())
                }
//                播放完动画后再传递返回参数
                stack = it

//                前进动作, 先传递参数, 再播放动画
                if (direction == 1) {
                    delay(pageTransitionDurationMillis.toLong())

                }
//                如果去掉此处的 延迟, 会导致依赖 direction 的 LaunchedEffect 的正在运行的任务被取消, 产生动画的卡顿
                delay(pageTransitionDurationMillis.toLong())
                direction = 0
            }
        }, scope)
    }


    BackHandler {
//        TODO 如果上一个界面正在等待返回值, 那么应该取消此处的返回
        router.back()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        stack.forEachIndexed { index, pair ->
            key(index) {
                CompositionLocalProvider(
                    LocalRouter provides router
                ) {
                    val anim = remember {
                        Animatable(
                            if (index == 0) 0 else screenWidth,
                            Int.VectorConverter
                        )
                    }
                    LaunchedEffect(Unit) {
//                        入场第一次创建-动画
                        if (index == 0) return@LaunchedEffect
//                        delay(2000L)
                        anim.animateTo(
                            0,
                            TweenSpec(
                                pageTransitionDurationMillis,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    LaunchedEffect(direction) {
//                        退场但不销毁-动画
                        if (direction == 1 && index == stack.size - 2) {
//                            delay(400)
                            scope.launch {
                                anim.animateTo(
                                    screenWidth / -3,
                                    TweenSpec(
                                        pageTransitionDurationMillis,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            }
                        }
                    }
                    LaunchedEffect(direction) {
                        if (direction == -1 && index == stack.size - 1) {
//                            退场销毁
//                            top stack
                            anim.animateTo(
                                screenWidth,
                                TweenSpec(
                                    pageTransitionDurationMillis,
                                    easing = FastOutSlowInEasing
                                )
                            )
                            return@LaunchedEffect
                        }
                        if (direction == -1 && index == stack.size - 2) {
//                            bottom of top stack
//                            重新入场动画
//                            scope.launch {
//                            LogUtils.d("重新入场:开始", anim.value)
                            anim.animateTo(
                                0,
                                TweenSpec(
                                    pageTransitionDurationMillis,
                                    easing = FastOutSlowInEasing
                                )
                            )
//                            LogUtils.d("重新入场:完毕", anim.value)
//                            }
                            return@LaunchedEffect
                        }

                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .graphicsLayer {
                                translationX = anim.value.toFloat()
                            }
                            .background(Color.White)
                            .noRippleClickable {}
                    ) {
                        key(true) {
                            (pair.first as Page<Any?, Any?>).content.invoke(
                                this,
                                pair.second,
                                router
                            )
                        }

                        if (index == stack.size - 2) {
                            Box(modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = 0.5f * (anim.value / (screenWidth / -3f))
                                }
                                .background(Color.Black)
                                .noRippleClickable {}
                            )
                        }
                    }
                }

            }
        }
    }
}

const val pageTransitionDurationMillis = 300


