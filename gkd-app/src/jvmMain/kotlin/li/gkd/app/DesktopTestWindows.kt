package li.gkd.app

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import java.awt.event.WindowEvent
import java.util.WeakHashMap
import javax.swing.SwingUtilities

/** Prevent even newly created Compose popup/dialog windows from activating during --test. */
object DesktopTestWindows {
    var activations: Int = 0
        private set
    private val inputWindows = WeakHashMap<Window, Boolean>()
    fun acceptsInput(window: Window): Boolean = inputWindows[window] ?: window.focusableWindowState
    fun install(): AutoCloseable {
        val toolkit = Toolkit.getDefaultToolkit()
        val listener = AWTEventListener { event ->
            if (event is WindowEvent && event.id == WindowEvent.WINDOW_ACTIVATED) activations++
            if (event is HierarchyEvent && event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() != 0L) {
                val window =
                    event.component as? Window ?: SwingUtilities.getWindowAncestor(event.component)
                window?.let {
                    if (!inputWindows.containsKey(it)) {
                        inputWindows[it] = it.focusableWindowState
                        // Compose can update popup focusability after addNotify, before show.
                        it.addPropertyChangeListener("focusableWindowState") { change ->
                            if (change.newValue == true) {
                                inputWindows[it] = true
                                it.focusableWindowState = false
                            }
                        }
                    }
                    it.isAutoRequestFocus = false
                    it.focusableWindowState = false
                }
            }
        }
        toolkit.addAWTEventListener(
            listener,
            AWTEvent.HIERARCHY_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK
        )
        return AutoCloseable { toolkit.removeAWTEventListener(listener) }
    }
}
