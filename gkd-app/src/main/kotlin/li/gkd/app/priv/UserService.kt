package li.gkd.app.priv

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ServiceSpecificException
import androidx.annotation.Keep

private const val SCREENSHOT_ERROR_CODE = 1
private const val MAX_SCREENSHOT_ERROR_LENGTH = 16_384

@Keep
class UserService : IUserService.Stub() {
    override fun takeScreenshot(crop: Rect, rotation: Int): Bitmap? {
        return try {
            CompatScreenshot.captureBySurfaceControl(crop, rotation)
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: ThreadDeath) {
            throw e
        } catch (e: Throwable) {
            throw ServiceSpecificException(
                SCREENSHOT_ERROR_CODE,
                e.stackTraceToString().take(MAX_SCREENSHOT_ERROR_LENGTH),
            )
        }
    }

    override fun destroy() = Unit
}
