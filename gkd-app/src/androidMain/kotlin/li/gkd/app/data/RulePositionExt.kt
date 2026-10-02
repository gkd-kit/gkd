package li.gkd.app.data

import android.graphics.Rect
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.RuleBounds
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ScreenUtils
import li.gkd.app.util.ToastUtils

fun RawSubscription.Position.calc(rect: Rect): Pair<Float, Float>? = calc(
    RuleBounds(rect.left, rect.top, rect.right, rect.bottom),
    ScreenUtils.getWidth(), ScreenUtils.getHeight(),
) { error ->
    LogUtils.d("Position.calc", error)
    ToastUtils.show(error.message ?: error.stackTraceToString())
}
