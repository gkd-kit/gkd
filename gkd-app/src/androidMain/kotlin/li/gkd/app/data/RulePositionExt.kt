package li.gkd.app.data

import li.gkd.app.model.ScreenSize
import android.graphics.Rect
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.RuleBounds
import li.gkd.app.logging.LogUtils
import li.gkd.app.util.ScreenUtils
import li.gkd.app.util.ToastUtils

fun RawSubscription.Position.calc(rect: Rect, screenSize: ScreenSize = ScreenUtils.getSize()): Pair<Float, Float>? = calc(
    RuleBounds(rect.left, rect.top, rect.right, rect.bottom),
    screenSize,
) { error ->
    LogUtils.d("Position.calc", error)
    ToastUtils.show(error.message ?: error.stackTraceToString())
}
