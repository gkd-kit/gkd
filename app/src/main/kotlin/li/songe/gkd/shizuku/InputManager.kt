package li.songe.gkd.shizuku

import android.content.Context
import android.hardware.input.IInputManager
import androidx.annotation.WorkerThread
import li.songe.gkd.util.AndroidTarget

class SafeInputManager(private val value: IInputManager) {
    companion object {
        fun newBinder() = getShizukuService(Context.INPUT_SERVICE)?.let {
            SafeInputManager(IInputManager.Stub.asInterface(it))
        }
    }

    private val compat = InputManagerCompat(value)

    @WorkerThread
    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        if (!AndroidTarget.S) {
            return compat.tap(x, y, duration)
        }
        return if (duration > 0) {
            value.asBinder().shellCommand(
                "swipe",
                x.toString(),
                y.toString(),
                x.toString(),
                y.toString(),
                duration.toString(),
            ).ok
        } else {
            value.asBinder().shellCommand("tap", x.toString(), y.toString()).ok
        }
    }

    @WorkerThread
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        if (!AndroidTarget.S) {
            return compat.swipe(x1, y1, x2, y2, duration)
        }
        return value.asBinder().shellCommand(
            "swipe",
            x1.toString(),
            y1.toString(),
            x2.toString(),
            y2.toString(),
            duration.toString(),
        ).ok
    }

    fun key(keyCode: Int): Boolean {
        if (!AndroidTarget.S) {
            return compat.key(keyCode)
        }
        return value.asBinder().shellCommand("keyevent", keyCode.toString()).ok
    }
}
