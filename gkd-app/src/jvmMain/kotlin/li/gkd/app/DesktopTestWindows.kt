package li.gkd.app

import java.awt.AWTEvent
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities

/** Keep --test windows in the background until the user activates them. */
object DesktopTestWindows {
    var activations: Int = 0
        private set

    fun install(): AutoCloseable {
        val toolkit = Toolkit.getDefaultToolkit()
        val listener = AWTEventListener { event ->
            if (event is WindowEvent && event.id == WindowEvent.WINDOW_ACTIVATED) {
                activations++
                val window = event.window
                // Background test frames start off-screen; taskbar activation must reveal them.
                if (
                    window is Frame &&
                        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.none {
                            it.defaultConfiguration.bounds.intersects(window.bounds)
                        }
                ) {
                    window.setLocationRelativeTo(null)
                }
            }
            if (
                event is HierarchyEvent &&
                    event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() != 0L
            ) {
                val window =
                    event.component as? Window ?: SwingUtilities.getWindowAncestor(event.component)
                window?.let {
                    // Popups may take focus within an already active app, but never from another
                    // app.
                    it.isAutoRequestFocus =
                        generateSequence(it.owner) { owner -> owner.owner }
                            .any { owner -> owner.isActive }
                }
            }
        }
        toolkit.addAWTEventListener(
            listener,
            AWTEvent.HIERARCHY_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK,
        )
        return AutoCloseable { toolkit.removeAWTEventListener(listener) }
    }
}
