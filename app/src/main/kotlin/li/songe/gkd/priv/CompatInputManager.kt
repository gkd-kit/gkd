package li.songe.gkd.priv

import android.content.Context
import android.hardware.input.IInputManager
import android.hardware.input.InputManagerHidden
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import li.songe.gkd.util.AndroidTarget
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatInputManager {
    val value: IInputManager = IInputManager.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.INPUT_SERVICE),
        ),
    )
    private val legacy = LegacyInputInjector(value)

    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        return if (AndroidTarget.S) {
            if (duration > 0) {
                swipe(x, y, x, y, duration)
            } else {
                value.asBinder().shellCommand("tap", x.toString(), y.toString()).ok
            }
        } else {
            legacy.tap(x, y, duration)
        }
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        return if (AndroidTarget.S) {
            value.asBinder().shellCommand(
                "swipe",
                x1.toString(),
                y1.toString(),
                x2.toString(),
                y2.toString(),
                duration.toString(),
            ).ok
        } else {
            legacy.swipe(x1, y1, x2, y2, duration)
        }
    }

    fun keyevent(keyCode: Int): Boolean {
        return if (AndroidTarget.S) {
            value.asBinder().shellCommand("keyevent", keyCode.toString()).ok
        } else {
            legacy.keyevent(keyCode)
        }
    }
}

// Android 8-11 compatibility path based on cmds/input Input.java.
@Suppress("SameParameterValue")
private class LegacyInputInjector(private val value: IInputManager) {
    companion object {
        private const val DEFAULT_DEVICE_ID = 0
        private const val DEFAULT_SIZE = 1.0f
        private const val DEFAULT_META_STATE = 0
        private const val DEFAULT_PRECISION_X = 1.0f
        private const val DEFAULT_PRECISION_Y = 1.0f
        private const val DEFAULT_EDGE_FLAGS = 0
    }

    fun tap(x: Float, y: Float, duration: Long): Boolean {
        return if (duration > 0) {
            swipe(x, y, x, y, duration)
        } else {
            sendTap(x, y)
        }
    }

    fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        duration: Long,
    ): Boolean {
        val safeDuration = if (duration < 0) 300L else duration
        val downTime = SystemClock.uptimeMillis()
        var success = injectMotionEvent(
            inputSource = InputDevice.SOURCE_TOUCHSCREEN,
            action = MotionEvent.ACTION_DOWN,
            downTime = downTime,
            eventTime = downTime,
            x = x1,
            y = y1,
            pressure = 1.0f,
        )
        var now = SystemClock.uptimeMillis()
        val endTime = downTime + safeDuration
        while (now < endTime) {
            val elapsedTime = now - downTime
            val alpha = elapsedTime.toFloat() / safeDuration
            success = injectMotionEvent(
                inputSource = InputDevice.SOURCE_TOUCHSCREEN,
                action = MotionEvent.ACTION_MOVE,
                downTime = downTime,
                eventTime = now,
                x = lerp(x1, x2, alpha),
                y = lerp(y1, y2, alpha),
                pressure = 1.0f,
            ) && success
            now = SystemClock.uptimeMillis()
        }
        return injectMotionEvent(
            inputSource = InputDevice.SOURCE_TOUCHSCREEN,
            action = MotionEvent.ACTION_UP,
            downTime = downTime,
            eventTime = now,
            x = x2,
            y = y2,
            pressure = 0.0f,
        ) && success
    }

    fun keyevent(keyCode: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(
            now,
            now,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            0,
            InputDevice.SOURCE_KEYBOARD,
        )
        if (AndroidTarget.Q) {
            event.toHidden.setDisplayId(Display.INVALID_DISPLAY)
        }
        val downSuccess = injectInputEvent(event)
        val upSuccess = injectInputEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP))
        return downSuccess && upSuccess
    }

    private fun sendTap(x: Float, y: Float): Boolean {
        val now = SystemClock.uptimeMillis()
        val downSuccess = injectMotionEvent(
            inputSource = InputDevice.SOURCE_TOUCHSCREEN,
            action = MotionEvent.ACTION_DOWN,
            downTime = now,
            eventTime = now,
            x = x,
            y = y,
            pressure = 1.0f,
        )
        val upSuccess = injectMotionEvent(
            inputSource = InputDevice.SOURCE_TOUCHSCREEN,
            action = MotionEvent.ACTION_UP,
            downTime = now,
            eventTime = now,
            x = x,
            y = y,
            pressure = 0.0f,
        )
        return downSuccess && upSuccess
    }

    private fun injectMotionEvent(
        inputSource: Int,
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
        pressure: Float,
    ): Boolean {
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            x,
            y,
            pressure,
            DEFAULT_SIZE,
            DEFAULT_META_STATE,
            DEFAULT_PRECISION_X,
            DEFAULT_PRECISION_Y,
            getInputDeviceId(inputSource),
            DEFAULT_EDGE_FLAGS,
        )
        return try {
            event.source = inputSource
            if (AndroidTarget.Q) {
                var displayId = Display.INVALID_DISPLAY
                if ((inputSource and InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    displayId = Display.DEFAULT_DISPLAY
                }
                event.toHidden.setDisplayId(displayId)
            }
            injectInputEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun injectInputEvent(event: InputEvent): Boolean {
        return try {
            value.injectInputEvent(
                event,
                InputManagerHidden.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH,
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    private fun getInputDeviceId(inputSource: Int): Int {
        for (devId in InputDevice.getDeviceIds()) {
            val inputDevice = InputDevice.getDevice(devId) ?: continue
            if (inputDevice.supportsSource(inputSource)) {
                return devId
            }
        }
        return DEFAULT_DEVICE_ID
    }

    private fun lerp(a: Float, b: Float, alpha: Float): Float {
        return (b - a) * alpha + a
    }
}
