package li.gkd.app.util

import com.hjq.toast.Toaster
import li.gkd.app.platform.lifecycle.MainActivityVisibility

actual fun showPlatformToast(text: CharSequence, forced: Boolean) {
    if (forced || MainActivityVisibility.isVisible) {
        Toaster.show(text)
    }
}
