package li.gkd.app.data

import android.graphics.Rect
import li.gkd.app.model.ScreenSize
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.RuleBounds
import li.gkd.app.util.ScreenUtils

fun RawSubscription.Position.calc(
    rect: Rect,
    fieldPrefix: String = "position",
    captureInputs: Boolean = false,
    screenSize: ScreenSize = ScreenUtils.getSize(),
): Pair<Float, Float> =
    calc(
        RuleBounds(rect.left, rect.top, rect.right, rect.bottom),
        screenSize,
        fieldPrefix,
        captureInputs,
    )
