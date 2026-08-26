package li.songe.gkd.priv

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.annotation.Keep

@Keep
class UserService : IUserService.Stub() {
    override fun takeScreenshot(crop: Rect, rotation: Int): Bitmap? {
        return CompatScreenshot.captureBySurfaceControl(crop, rotation)
    }

    override fun destroy() = Unit
}
