package li.gkd.app.priv

import android.graphics.Matrix
import android.os.RemoteException
import android.os.SystemClock
import android.view.InputWindowHandle
import android.window.WindowInfosListener
import li.gkd.aidl.ISystemSurfaceListener

/** Runs in UserService: native SurfaceFlinger registration uses the calling process UID. */
class PrivilegedSystemSurfaceListener(private val receiver: ISystemSurfaceListener) :
    WindowInfosListener() {
    private var active = true
    private var previous: Int? = null

    @Synchronized
    override fun onWindowInfosChanged(
        windows: Array<InputWindowHandle>,
        displays: Array<DisplayInfo>,
    ) {
        if (!active) return
        val received = SystemClock.uptimeMillis()
        val display = displays.firstOrNull { it.mDisplayId == 0 }
        val state =
            if (display == null || windows.isEmpty()) -1
            else {
                val inverse = Matrix()
                if (!display.mTransform.invert(inverse)) -1
                else {
                    val center =
                        floatArrayOf(
                            display.mLogicalSize.width / 2f,
                            display.mLogicalSize.height / 2f,
                        )
                    inverse.mapPoints(center)
                    val x = center[0].toInt()
                    val y = center[1].toInt()
                    // Reverse Z order. Ignore non-input, invisible and non-touchable surfaces.
                    // Center hit testing excludes status-bar islands and the hollow region during
                    // collapse.
                    val top = windows.firstOrNull {
                        it.displayId == 0 &&
                            it.inputConfig and (1 or 2 or 8) == 0 &&
                            it.touchableRegion.contains(x, y) &&
                            (it.layoutParamsType in 1..4 ||
                                (it.layoutParamsType == 2040 &&
                                    it.packageName == "com.android.systemui"))
                    }
                    when {
                        top == null -> -1
                        top.layoutParamsType == 2040 -> 1
                        else -> 0
                    }
                }
            }
        if (previous == state) return
        previous = state
        try {
            receiver.onSurfaceChanged(state, received)
        } catch (_: RemoteException) {
            // The owner disappeared; its UserService teardown unregisters the native listener.
            active = false
        }
    }

    fun start() {
        // Wait for a live callback: cached registration data can be empty or older than a callback
        // already in flight, and does not prove successful registration.
        register()
    }

    fun close() {
        synchronized(this) { active = false }
        unregister()
    }
}
