package li.songe.gkd.shizuku

import android.content.Context
import android.hardware.input.IInputManager
import android.view.InputEvent
import androidx.annotation.WorkerThread
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.checkExistClass


class SafeInputManager(private val value: IInputManager) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("android.hardware.input.IInputManager")

        fun newBinder() = getStubService(
            Context.INPUT_SERVICE,
            isAvailable,
        )?.let {
            SafeInputManager(IInputManager.Stub.asInterface(it))
        }
    }

    private val command = InputShellCommand(this)

    fun compatInjectInputEvent(
        ev: InputEvent,
        mode: Int,
    ) = safeInvokeMethod {
        if (AndroidTarget.TIRAMISU) {
            // https://github.com/android-cs/16/blob/main/core/java/android/hardware/input/InputManagerGlobal.java#L1707
            value.injectInputEventToTarget(ev, mode, android.os.Process.INVALID_UID)
        } else {
            value.injectInputEvent(ev, mode)
        }
    }

    @WorkerThread
    fun tap(x: Float, y: Float, duration: Long = 0) {
        if (duration > 0) {
            command.runSwipe(x, y, x, y, duration)
        } else {
            command.runTap(x, y)
        }
    }

}