package li.songe.gkd.util

import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import li.songe.gkd.app

/**
 * px -> dp
 */
val Dp.px: Float
    get() = value * app.resources.displayMetrics.density

/**
 * sp -> px
 */
val TextUnit.px: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value, app.resources.displayMetrics
    )

///**
// * px -> dp
// */
//val Int.calcDp: Float
//    get() = this / app.resources.displayMetrics.density

