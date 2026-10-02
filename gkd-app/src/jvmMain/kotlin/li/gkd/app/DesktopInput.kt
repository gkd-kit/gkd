package li.gkd.app

import li.gkd.app.window.DesktopWindowGeometry
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities

/** Delivers to the same AWT listeners as physical input without using the system input queue. */
object DesktopInput {
    fun layer(component: Component): SkiaLayer? = when (component) {
        is SkiaLayer -> component
        is Container -> component.components.firstNotNullOfOrNull(::layer)
        else -> null
    }

    // SwingGraphics ComposePanel uses a lightweight event receiver instead of a SkiaLayer canvas.
    private fun inputComponent(component: Component): Component? = layer(component)?.canvas
        ?: (component as? Container)?.components?.firstNotNullOfOrNull(::inputComponent)
        ?: component.takeIf { it.keyListeners.isNotEmpty() && it.mouseListeners.isNotEmpty() }

    fun key(window: Window, key: String) {
        val code = when (key) {
            "Tab" -> KeyEvent.VK_TAB
            "Enter" -> KeyEvent.VK_ENTER
            "Escape" -> KeyEvent.VK_ESCAPE
            "Space" -> KeyEvent.VK_SPACE
            "Backspace" -> KeyEvent.VK_BACK_SPACE
            "F5" -> KeyEvent.VK_F5
            "F12" -> KeyEvent.VK_F12
            else -> throw IllegalArgumentException("Unsupported key: $key")
        }
        val canvas =
            requireNotNull(inputComponent(window)) { "Target has no Compose input component" }
        require(canvas.keyListeners.isNotEmpty()) { "Target has no Compose key listener" }
        // AWT's global KeyboardFocusManager would discard/retarget an inactive window's keys.
        // These registered listeners feed Compose's normal key and text-input pipeline instead.
        val pressed = KeyEvent(
            canvas,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            code,
            KeyEvent.CHAR_UNDEFINED
        )
        canvas.keyListeners.forEach { it.keyPressed(pressed) }
        val released = KeyEvent(
            canvas,
            KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(),
            0,
            code,
            KeyEvent.CHAR_UNDEFINED
        )
        canvas.keyListeners.forEach { it.keyReleased(released) }
    }

    fun pointer(window: Window, request: UiAction) {
        require(SwingUtilities.isEventDispatchThread())
        require(request.area == "content") { "Native window frame input requires an isolated desktop; background input supports content only" }
        val bounds = DesktopWindowGeometry.contentBounds(window)
        require(request.x in 0 until bounds.width && request.y in 0 until bounds.height)
        val canvas =
            requireNotNull(inputComponent(window)) { "Target has no Compose input component" }
        val origin = canvas.locationOnScreen
        fun point(x: Int, y: Int) = Point(bounds.x + x - origin.x, bounds.y + y - origin.y)
        fun mouse(
            id: Int,
            x: Int,
            y: Int,
            button: Int = MouseEvent.NOBUTTON,
            count: Int = 0,
            modifiers: Int = 0
        ) {
            val p = point(x, y)
            val event = MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiers,
                p.x, p.y, bounds.x + x, bounds.y + y, count, false, button
            )
            canvas.dispatchEvent(event)
        }
        mouse(MouseEvent.MOUSE_MOVED, request.x, request.y)
        when (request.type) {
            "click", "rightClick", "doubleClick" -> {
                val button =
                    if (request.type == "rightClick") MouseEvent.BUTTON3 else MouseEvent.BUTTON1
                val mask = InputEvent.getMaskForButton(button)
                repeat(if (request.type == "doubleClick") 2 else 1) { index ->
                    mouse(MouseEvent.MOUSE_PRESSED, request.x, request.y, button, index + 1, mask)
                    mouse(MouseEvent.MOUSE_RELEASED, request.x, request.y, button, index + 1)
                    mouse(MouseEvent.MOUSE_CLICKED, request.x, request.y, button, index + 1)
                }
            }

            "drag" -> {
                require(request.endX in -bounds.width..bounds.width * 2 && request.endY in -bounds.height..bounds.height * 2)
                mouse(
                    MouseEvent.MOUSE_PRESSED,
                    request.x,
                    request.y,
                    MouseEvent.BUTTON1,
                    1,
                    InputEvent.BUTTON1_DOWN_MASK
                )
                try {
                    repeat(12) { step ->
                        mouse(
                            MouseEvent.MOUSE_DRAGGED,
                            request.x + (request.endX - request.x) * (step + 1) / 12,
                            request.y + (request.endY - request.y) * (step + 1) / 12,
                            modifiers = InputEvent.BUTTON1_DOWN_MASK
                        )
                    }
                } finally {
                    mouse(
                        MouseEvent.MOUSE_RELEASED,
                        request.endX,
                        request.endY,
                        MouseEvent.BUTTON1,
                        1
                    )
                }
            }

            "scroll" -> {
                require(request.amount in -100..100)
                // Compose installs its wheel listener on the canvas container for Swing interop.
                val receiver = generateSequence(canvas) { it.parent }
                    .firstOrNull { it.mouseWheelListeners.isNotEmpty() }
                requireNotNull(receiver) { "Target has no Compose wheel listener" }
                val p = SwingUtilities.convertPoint(canvas, point(request.x, request.y), receiver)
                receiver.dispatchEvent(
                    MouseWheelEvent(
                        receiver, MouseEvent.MOUSE_WHEEL,
                        System.currentTimeMillis(), 0, p.x, p.y, 0, false,
                        MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, request.amount
                    )
                )
            }

            "hover" -> Unit
            else -> throw IllegalArgumentException("Unsupported pointer action")
        }
    }
}
