package li.gkd.app.development

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlinx.serialization.Serializable
import li.gkd.app.META
import li.gkd.app.settings.SettingsRepository

@Serializable
data class GestureTouch(
    val action: Int,
    val time: Long,
    val downTime: Long,
    val x: Float,
    val y: Float,
    val pointers: Int,
)

@Serializable
data class GestureSurfaceState(
    val active: Boolean,
    val down: Boolean,
    val bounds: List<Int>,
    val now: Long,
    val dropped: Int,
    val events: List<GestureTouch>,
)

/** Opt-in Debug activity observations; never observes touches in production windows. */
object GestureDebugSurface {
    private var owner: Any? = null
    private var down = false
    private var bounds = emptyList<Int>()
    private var dropped = 0
    private val events = ArrayDeque<GestureTouch>()

    @Synchronized
    fun attach(token: Any) {
        check(owner == null)
        owner = token
        down = false
        bounds = emptyList()
        events.clear()
        dropped = 0
    }

    @Synchronized
    fun detach(token: Any) {
        if (owner === token) owner = null
    }

    @Synchronized
    fun layout(token: Any, value: List<Int>) {
        if (owner === token) bounds = value
    }

    @Synchronized
    fun record(token: Any, event: MotionEvent) {
        if (owner !== token) return
        if (event.actionMasked == MotionEvent.ACTION_DOWN) down = true
        if (
            event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
        )
            down = false
        if (events.size == 1024) {
            events.removeFirst()
            dropped++
        }
        events.addLast(
            GestureTouch(
                event.actionMasked,
                event.eventTime,
                event.downTime,
                event.rawX,
                event.rawY,
                event.pointerCount,
            ),
        )
    }

    @Synchronized
    fun snapshot() =
        GestureSurfaceState(
            owner != null,
            down,
            bounds,
            SystemClock.uptimeMillis(),
            dropped,
            events.toList(),
        )

    @Synchronized
    fun reset(): GestureSurfaceState {
        check(owner != null && !down) { "Gesture surface is inactive or still has a down pointer" }
        events.clear()
        dropped = 0
        return snapshot()
    }
}

/** Registered only by the Debug manifest; a consuming surface with no clickable controls. */
class GestureDebugActivity : Activity() {
    private val token = Any()
    private var attached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!META.debuggable || SettingsRepository.settings.value.enableMatch) {
            finish()
            return
        }
        GestureDebugSurface.attach(token)
        attached = true
        setContentView(
            object : View(this) {
                private val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.CYAN
                        strokeWidth = 6f
                    }
                private var x = -1f
                private var y = -1f

                init {
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                }

                override fun onLayout(
                    changed: Boolean,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                ) {
                    val location = IntArray(2)
                    getLocationOnScreen(location)
                    GestureDebugSurface.layout(
                        token,
                        listOf(location[0], location[1], width, height),
                    )
                }

                override fun onTouchEvent(event: MotionEvent): Boolean {
                    GestureDebugSurface.record(token, event)
                    x = event.x
                    y = event.y
                    invalidate()
                    return true
                }

                override fun onDraw(canvas: Canvas) {
                    canvas.drawColor(Color.DKGRAY)
                    if (x >= 0) canvas.drawCircle(x, y, 18f, paint)
                }
            },
        )
    }

    override fun onDestroy() {
        if (attached) GestureDebugSurface.detach(token)
        super.onDestroy()
    }
}
