package li.gkd.app.util

import li.gkd.app.DesktopRuntime

actual fun showPlatformToast(text: CharSequence, forced: Boolean) {
    DesktopRuntime.requireCurrent().toast.show(text.toString())
}
