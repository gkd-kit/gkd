package li.gkd.app.util

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import li.gkd.app.app

object KeyboardUtils {
    fun hide(activity: Activity) {
        hide(activity.window)
    }

    fun hide(window: Window) {
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
        hide(view)
    }

    fun hide(view: View) {
        app.inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
