package li.songe.gkd.shizuku

import android.hardware.input.InputManagerHidden
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.KeyEventHidden
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.MotionEventHidden
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import dev.rikka.tools.refine.Refine
import li.songe.gkd.util.AndroidTarget
import java.util.Map
import kotlin.math.floor


// https://github.com/android-cs/16/blob/main/services/core/java/com/android/server/input/InputShellCommand.java
@Suppress("SameParameterValue")
class InputShellCommand(val safeInputManager: SafeInputManager) {
    companion object {
        private const val DEFAULT_DEVICE_ID = 0
        private const val DEFAULT_SIZE = 1.0f
        private const val DEFAULT_META_STATE = 0
        private const val DEFAULT_PRECISION_X = 1.0f
        private const val DEFAULT_PRECISION_Y = 1.0f
        private const val DEFAULT_EDGE_FLAGS = 0
        private const val DEFAULT_BUTTON_STATE = 0
        private const val DEFAULT_FLAGS = 0
        private const val SECOND_IN_MILLISECONDS = 1000L
        private const val SWIPE_EVENT_HZ_DEFAULT = 120
    }

    fun runTap(x: Float, y: Float) {
        sendTap(InputDevice.SOURCE_TOUCHSCREEN, x, y, Display.INVALID_DISPLAY)
    }

    fun runSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        sendSwipe(
            InputDevice.SOURCE_TOUCHSCREEN,
            x1,
            y1,
            x2,
            y2,
            duration,
            Display.INVALID_DISPLAY,
            false,
        )
    }

    private fun sendSwipe(
        inputSource: Int,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        duration: Long,
        displayId: Int,
        isDragDrop: Boolean,
    ) {
        val down = SystemClock.uptimeMillis()
        injectMotionEvent(
            inputSource, MotionEvent.ACTION_DOWN, down, down, x1, y1, 1.0f,
            displayId
        )
        if (isDragDrop) {
            // long press until drag start.
            sleep(ViewConfiguration.getLongPressTimeout().toLong())
        }
        var now = SystemClock.uptimeMillis()
        val endTime = down + duration
        val swipeEventPeriodMillis: Float =
            SECOND_IN_MILLISECONDS.toFloat() / SWIPE_EVENT_HZ_DEFAULT
        var injected = 1
        while (now < endTime) {
            // Ensure that we inject at most at the frequency of SWIPE_EVENT_HZ_DEFAULT
            // by waiting an additional delta between the actual time and expected time.
            var elapsedTime = now - down
            val errorMillis =
                floor((injected * swipeEventPeriodMillis - elapsedTime).toDouble()).toLong()
            if (errorMillis > 0) {
                // Make sure not to exceed the duration and inject an extra event.
                if (errorMillis > endTime - now) {
                    sleep(endTime - now)
                    break
                }
                sleep(errorMillis)
            }
            now = SystemClock.uptimeMillis()
            elapsedTime = now - down
            val alpha = elapsedTime.toFloat() / duration
            injectMotionEvent(
                inputSource, MotionEvent.ACTION_MOVE, down, now,
                lerp(x1, x2, alpha), lerp(y1, y2, alpha), 1.0f, displayId
            )
            injected++
            now = SystemClock.uptimeMillis()
        }
        injectMotionEvent(
            inputSource, MotionEvent.ACTION_UP, down, now, x2, y2, 0.0f,
            displayId
        )
    }

    private fun sendTap(
        inputSource: Int,
        x: Float,
        y: Float,
        displayId: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        injectMotionEvent(inputSource, MotionEvent.ACTION_DOWN, now, now, x, y, 1.0f, displayId)
        injectMotionEvent(inputSource, MotionEvent.ACTION_UP, now, now, x, y, 0.0f, displayId)
    }

    private fun injectMotionEvent(
        inputSource: Int,
        action: Int,
        downTime: Long,
        mWhen: Long,
        x: Float,
        y: Float,
        pressure: Float,
        displayId: Int
    ) {
        if (AndroidTarget.S) {
            val axisValues = Map.of<Int, Float>(
                MotionEvent.AXIS_X, x, MotionEvent.AXIS_Y, y, MotionEvent.AXIS_PRESSURE, pressure
            )
            injectMotionEvent(inputSource, action, downTime, mWhen, axisValues, displayId)
        } else {
            // https://github.com/android-cs/11/blob/main/cmds/input/src/com/android/commands/input/Input.java#L382
            val event = MotionEvent.obtain(
                downTime, mWhen, action, x, y, pressure, DEFAULT_SIZE,
                DEFAULT_META_STATE, DEFAULT_PRECISION_X, DEFAULT_PRECISION_Y,
                getInputDeviceId(inputSource), DEFAULT_EDGE_FLAGS
            )
            event.setSource(inputSource)
            // https://github.com/android-cs/9/blob/main/cmds/input/src/com/android/commands/input/Input.java#L298
            if (AndroidTarget.Q) {
                var mDisplayId = displayId
                if (mDisplayId == Display.INVALID_DISPLAY && (inputSource and InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    mDisplayId = Display.DEFAULT_DISPLAY
                }
                Refine.unsafeCast<MotionEventHidden>(event).setDisplayId(mDisplayId)
            }
            safeInputManager.compatInjectInputEvent(
                event, InputManagerHidden.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("KotlinConstantConditions")
    private fun injectMotionEvent(
        inputSource: Int,
        action: Int,
        downTime: Long,
        mWhen: Long,
        axisValues: MutableMap<Int, Float>,
        displayId: Int
    ) {
        val pointerCount = 1
        val pointerProperties = arrayOfNulls<PointerProperties>(pointerCount)
        for (i in 0..<pointerCount) {
            pointerProperties[i] = PointerProperties().apply {
                id = i
                toolType = getToolType(inputSource)
            }
        }
        val pointerCoords = arrayOfNulls<PointerCoords>(pointerCount)
        for (i in 0..<pointerCount) {
            pointerCoords[i] = PointerCoords().apply {
                size = DEFAULT_SIZE
                for (entry in axisValues.entries) {
                    setAxisValue(entry.key, entry.value)
                }
            }
        }
        var mDisplayId = displayId
        if (mDisplayId == Display.INVALID_DISPLAY && (inputSource and InputDevice.SOURCE_CLASS_POINTER) != 0) {
            mDisplayId = Display.DEFAULT_DISPLAY
        }
        val event = MotionEventHidden.obtain(
            downTime,
            mWhen,
            action,
            pointerCount,
            pointerProperties,
            pointerCoords,
            DEFAULT_META_STATE,
            DEFAULT_BUTTON_STATE,
            DEFAULT_PRECISION_X,
            DEFAULT_PRECISION_Y,
            getInputDeviceId(inputSource),
            DEFAULT_EDGE_FLAGS,
            inputSource,
            mDisplayId,
            DEFAULT_FLAGS,
        )
        safeInputManager.compatInjectInputEvent(
            event, InputManagerHidden.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
        )
    }

    private fun getInputDeviceId(inputSource: Int): Int {
        val devIds = InputDevice.getDeviceIds()
        for (devId in devIds) {
            val inputDev = InputDevice.getDevice(devId)!!
            if (inputDev.supportsSource(inputSource)) {
                return devId
            }
        }
        return DEFAULT_DEVICE_ID
    }

    private fun getToolType(inputSource: Int): Int = when (inputSource) {
        InputDevice.SOURCE_MOUSE, InputDevice.SOURCE_MOUSE_RELATIVE, InputDevice.SOURCE_TRACKBALL -> MotionEvent.TOOL_TYPE_MOUSE
        InputDevice.SOURCE_STYLUS, InputDevice.SOURCE_BLUETOOTH_STYLUS -> MotionEvent.TOOL_TYPE_STYLUS
        InputDevice.SOURCE_TOUCHPAD, InputDevice.SOURCE_TOUCHSCREEN, InputDevice.SOURCE_TOUCH_NAVIGATION -> MotionEvent.TOOL_TYPE_FINGER
        else -> MotionEvent.TOOL_TYPE_UNKNOWN
    }

    private fun sleep(milliseconds: Long) {
        try {
            Thread.sleep(milliseconds)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    private fun lerp(a: Float, b: Float, alpha: Float): Float {
        return (b - a) * alpha + a
    }

    fun runKeyEvent(keyCode: Int) {
        sendKeyEvent(keyCode)
    }

    private fun sendKeyEvent(keyCode: Int) {
        val inputSource = InputDevice.SOURCE_UNKNOWN
        val displayId = Display.INVALID_DISPLAY
        val async = false

        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(
            now, now, KeyEvent.ACTION_DOWN, keyCode, 0,
            0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
            inputSource
        )
        if (AndroidTarget.Q) {
            Refine.unsafeCast<KeyEventHidden>(event).setDisplayId(displayId)
        }
        injectKeyEvent(event, async)
        val event2 = KeyEvent.changeTimeRepeat(event, SystemClock.uptimeMillis(), 0)
        injectKeyEvent(KeyEvent.changeAction(event2, KeyEvent.ACTION_UP), async)
    }

    private fun injectKeyEvent(event: KeyEvent, async: Boolean) {
        val injectMode: Int = if (async) {
            InputManagerHidden.INJECT_INPUT_EVENT_MODE_ASYNC
        } else {
            InputManagerHidden.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
        }
        safeInputManager.compatInjectInputEvent(event, injectMode)
    }
}