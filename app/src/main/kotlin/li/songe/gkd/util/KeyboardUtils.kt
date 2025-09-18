package li.songe.gkd.util

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import li.songe.gkd.app
import kotlin.math.abs

object KeyboardUtils {
    private const val TAG_ON_GLOBAL_LAYOUT_LISTENER = -8
    private var sDecorViewDelta = 0
    private fun getDecorViewInvisibleHeight(window: Window): Int {
        val decorView = window.decorView
        val outRect = Rect()
        decorView.getWindowVisibleDisplayFrame(outRect)
        val delta = abs(decorView.bottom - outRect.bottom)
        if (delta <= BarUtils.getNavBarHeight() + BarUtils.getStatusBarHeight()) {
            sDecorViewDelta = delta
            return 0
        }
        return delta - sDecorViewDelta
    }

    fun registerSoftInputChangedListener(window: Window, onSoftInputChanged: (Int) -> Unit) {
        val flags = window.attributes.flags
        if ((flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
        val contentView = window.findViewById<View>(android.R.id.content)
        val decorViewInvisibleHeightPre = intArrayOf(getDecorViewInvisibleHeight(window))
        val onGlobalLayoutListener = OnGlobalLayoutListener {
            val height = getDecorViewInvisibleHeight(window)
            if (decorViewInvisibleHeightPre[0] != height) {
                onSoftInputChanged(height)
                decorViewInvisibleHeightPre[0] = height
            }
        }
        contentView.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener)
        contentView.setTag(TAG_ON_GLOBAL_LAYOUT_LISTENER, onGlobalLayoutListener)
    }


    fun hideSoftInput(activity: Activity) {
        hideSoftInput(activity.window)
    }

    fun hideSoftInput(window: Window) {
        val tempTag = "keyboardTagView"
        var view = window.currentFocus
        if (view == null) {
            val decorView = window.decorView
            val focusView = decorView.findViewWithTag<View?>(tempTag)
            if (focusView == null) {
                view = EditText(window.context)
                view.tag = tempTag
                (decorView as ViewGroup).addView(view, 0, 0)
            } else {
                view = focusView
            }
            view.requestFocus()
        }
        hideSoftInput(view)
    }

    fun hideSoftInput(view: View) {
        app.inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}