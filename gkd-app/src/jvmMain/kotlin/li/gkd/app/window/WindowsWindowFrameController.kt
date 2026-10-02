package li.gkd.app.window

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import org.jetbrains.skiko.SkiaLayer
import java.awt.Container
import java.util.concurrent.ConcurrentHashMap

/** Owns both callbacks strongly until they have been uninstalled. No shared state between windows. */
class WindowsWindowFrameController(private val window: ComposeWindow) : AutoCloseable {
    private val api get() = WindowsNativeApi.instance
    private val user get() = User32.INSTANCE
    private val handle = window.windowHandle
    private val hwnd = HWND(Pointer(handle))
    private val hooks = mutableListOf<Hook>()

    @Volatile
    var layout = WindowsWindowHitTest()
    private var closed = false

    fun install() {
        liveControllers[handle] = this
        try {
            val skia = requireNotNull(findSkia(window)) { "Skia canvas is unavailable" }
            hooks += Hook(hwnd, false).also { it.install() }
            hooks += Hook(HWND(Native.getComponentPointer(skia.canvas)), true).also { it.install() }
            check(
                WindowsDwmApi.instance.DwmExtendFrameIntoClientArea(
                    hwnd,
                    WindowsDwmApi.Margins()
                ) >= 0
            )
            refreshFrame()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    private fun refreshFrame() {
        user.SetWindowPos(
            hwnd,
            null,
            0,
            0,
            0,
            0,
            WinUser.SWP_FRAMECHANGED or WinUser.SWP_NOSIZE or WinUser.SWP_NOMOVE or WinUser.SWP_NOZORDER or WinUser.SWP_NOACTIVATE
        )
    }

    private fun hit(lParam: LPARAM): Int {
        val point =
            POINT(lParam.toInt().toShort().toInt(), (lParam.toLong() shr 16).toShort().toInt())
        api.ScreenToClient(hwnd, point)
        val rect = RECT()
        user.GetClientRect(hwnd, rect)
        val dpi = api.GetDpiForWindow(hwnd)
        val border =
            api.GetSystemMetricsForDpi(WinUser.SM_CXSIZEFRAME, dpi) + api.GetSystemMetricsForDpi(
                WinUser.SM_CXPADDEDBORDER,
                dpi
            )
        return layout.hit(
            point.x.toFloat(),
            point.y.toFloat(),
            rect.right,
            rect.bottom,
            border,
            api.IsZoomed(hwnd)
        )
    }

    private fun systemMenu(lParam: LPARAM) {
        val menu = api.GetSystemMenu(hwnd, false) ?: return
        val maximized = api.IsZoomed(hwnd)
        api.EnableMenuItem(
            menu,
            WindowsConstants.SC_RESTORE,
            if (maximized) WindowsConstants.MF_ENABLED else WindowsConstants.MF_GRAYED
        ) // Restore
        api.EnableMenuItem(
            menu,
            WindowsConstants.SC_MOVE,
            if (maximized) WindowsConstants.MF_GRAYED else WindowsConstants.MF_ENABLED
        ) // Move
        api.EnableMenuItem(
            menu,
            WindowsConstants.SC_SIZE,
            if (maximized) WindowsConstants.MF_GRAYED else WindowsConstants.MF_ENABLED
        ) // Size
        api.EnableMenuItem(
            menu,
            WinUser.SC_MAXIMIZE,
            if (maximized) WindowsConstants.MF_GRAYED else WindowsConstants.MF_ENABLED
        ) // Maximize
        val command = api.TrackPopupMenu(
            menu,
            WindowsConstants.TPM_RETURNCMD or WindowsConstants.TPM_RIGHTBUTTON,
            lParam.toInt().toShort().toInt(),
            (lParam.toLong() shr 16).toShort().toInt(),
            0,
            hwnd,
            null
        )
        if (command != 0) user.PostMessage(
            hwnd,
            WinUser.WM_SYSCOMMAND,
            WPARAM(command.toLong()),
            LPARAM(0)
        )
    }

    private inner class Hook(val target: HWND, val child: Boolean) : WinUser.WindowProc {
        private var previous: Pointer? = null
        fun install() {
            previous = requireNotNull(
                api.SetWindowLongPtrW(
                    target,
                    WindowsConstants.GWLP_WNDPROC,
                    CallbackReference.getFunctionPointer(this)
                )
            ) {
                "Cannot install Windows window callback: ${Native.getLastError()}"
            }
        }

        fun restore() {
            previous?.let {
                check(
                    !user.IsWindow(target) || api.SetWindowLongPtrW(
                        target,
                        WindowsConstants.GWLP_WNDPROC,
                        it
                    ) != null
                ) {
                    "Cannot restore Windows window callback: ${Native.getLastError()}"
                }
                previous = null
            }
        }

        private fun original(message: Int, w: WPARAM, l: LPARAM): LRESULT =
            api.CallWindowProcW(requireNotNull(previous), target, message, w, l)

        override fun callback(hwnd: HWND, message: Int, w: WPARAM, l: LPARAM): LRESULT = try {
            when {
                !child && message == WindowsConstants.WM_NCCALCSIZE && w.toLong() != 0L &&
                        user.GetWindowLong(
                            hwnd,
                            WinUser.GWL_STYLE
                        ) and WinUser.WS_CAPTION != 0 -> { // WM_NCCALCSIZE
                    val rect = Pointer(l.toLong())
                    val top = rect.getInt(4)
                    original(message, w, l)
                    // Retain native side/bottom borders and maximized work-area constraints.
                    val dpi = api.GetDpiForWindow(hwnd)
                    val topInset = if (api.IsZoomed(hwnd))
                        api.GetSystemMetricsForDpi(
                            WinUser.SM_CYSIZEFRAME,
                            dpi
                        ) + api.GetSystemMetricsForDpi(WinUser.SM_CXPADDEDBORDER, dpi) else 0
                    rect.setInt(4, top + topInset)
                    LRESULT(0)
                }

                message == WindowsConstants.WM_NCHITTEST -> { // WM_NCHITTEST
                    val hit = hit(l)
                    LRESULT(if (child && hit != WindowsConstants.HTCLIENT && hit != WindowsConstants.HTMAXBUTTON) WindowsConstants.HTTRANSPARENT.toLong() else hit.toLong())
                }

                message == WindowsConstants.WM_NCRBUTTONUP && w.toInt() == WindowsConstants.HTCAPTION -> { // WM_NCRBUTTONUP on HTCAPTION
                    systemMenu(l)
                    LRESULT(0)
                }

                child && (message == WindowsConstants.WM_NCMOUSEMOVE || message == WindowsConstants.WM_NCLBUTTONDOWN || message == WindowsConstants.WM_NCLBUTTONUP) -> { // Non-client maximize button input.
                    val point =
                        POINT(l.toInt().toShort().toInt(), (l.toLong() shr 16).toShort().toInt())
                    api.ScreenToClient(target, point)
                    val clientPoint =
                        LPARAM(((point.y and 0xffff) shl 16 or (point.x and 0xffff)).toLong())
                    val clientMessage = when (message) {
                        WindowsConstants.WM_NCLBUTTONDOWN -> WindowsConstants.WM_LBUTTONDOWN; WindowsConstants.WM_NCLBUTTONUP -> WindowsConstants.WM_LBUTTONUP; else -> WindowsConstants.WM_MOUSEMOVE
                    }
                    user.SendMessage(
                        target,
                        clientMessage,
                        WPARAM(if (message == WindowsConstants.WM_NCLBUTTONDOWN) WindowsConstants.MK_LBUTTON.toLong() else 0L),
                        clientPoint
                    )
                    if (message == WindowsConstants.WM_NCMOUSEMOVE) user.SendMessage(
                        this@WindowsWindowFrameController.hwnd,
                        message,
                        w,
                        l
                    )
                    LRESULT(0)
                }

                else -> original(message, w, l)
            }
        } catch (error: Throwable) {
            System.err.println("GKD window callback failed: $error")
            runCatching { original(message, w, l) }.getOrDefault(LRESULT(0))
        }
    }

    override fun close() {
        if (!closed) {
            // Restore children before the frame; retain callbacks if restoration ever fails.
            hooks.asReversed().forEach { it.restore() }
            hooks.clear()
            WindowsDwmApi.instance.DwmExtendFrameIntoClientArea(
                hwnd,
                WindowsDwmApi.Margins().apply { left = 0; right = 0; top = 0; bottom = 0 })
            refreshFrame()
            closed = true
            liveControllers.remove(handle, this)
        }
    }

    private fun findSkia(container: Container): SkiaLayer? {
        for (child in container.components) {
            if (child is SkiaLayer) return child
            if (child is Container) findSkia(child)?.let { return it }
        }
        return null
    }

    companion object {
        // A failed native unhook must never leave Windows pointing at a garbage-collected callback.
        private val liveControllers = ConcurrentHashMap<Long, WindowsWindowFrameController>()
    }
}
