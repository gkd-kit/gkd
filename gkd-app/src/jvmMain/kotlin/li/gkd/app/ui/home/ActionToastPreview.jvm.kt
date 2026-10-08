package li.gkd.app.ui.home

import li.gkd.app.DesktopRuntime

actual fun previewActionToast(text: String, system: Boolean) {
    DesktopRuntime.requireCurrent().toast.show(text)
}
