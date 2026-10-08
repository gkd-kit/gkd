package li.gkd.app.util

import com.hjq.toast.Toaster
import li.gkd.app.platform.lifecycle.MainActivityVisibility
import li.gkd.app.service.OverlayWindowService

actual fun showPlatformToast(text: CharSequence, forced: Boolean) {
    if (forced || MainActivityVisibility.isVisible || OverlayWindowService.isAnyAlive) {
        Toaster.show(text)
    }
}
