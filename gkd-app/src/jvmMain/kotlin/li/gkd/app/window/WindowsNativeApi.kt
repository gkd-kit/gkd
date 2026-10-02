package li.gkd.app.window

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef.HMENU
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

interface WindowsNativeApi : StdCallLibrary {
    fun SetWindowLongPtrW(window: HWND, index: Int, value: Pointer): Pointer?
    fun CallWindowProcW(
        previous: Pointer,
        window: HWND,
        message: Int,
        wParam: WPARAM,
        lParam: LPARAM
    ): LRESULT

    fun GetDpiForWindow(window: HWND): Int
    fun GetSystemMetricsForDpi(index: Int, dpi: Int): Int
    fun IsZoomed(window: HWND): Boolean
    fun ScreenToClient(window: HWND, point: POINT): Boolean
    fun GetSystemMenu(window: HWND, reset: Boolean): HMENU?
    fun EnableMenuItem(menu: HMENU, item: Int, flags: Int): Int
    fun TrackPopupMenu(
        menu: HMENU,
        flags: Int,
        x: Int,
        y: Int,
        reserved: Int,
        window: HWND,
        rect: Pointer?
    ): Int

    companion object {
        val instance: WindowsNativeApi by lazy {
            Native.load("user32", WindowsNativeApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}

interface WindowsDwmApi : StdCallLibrary {
    fun DwmExtendFrameIntoClientArea(window: HWND, margins: Margins): Int

    @Structure.FieldOrder("left", "right", "top", "bottom")
    class Margins : Structure() {
        // Extend the DWM frame over the custom caption. A 1px top margin renders correctly
        // but prevents cold-hover Snap Layouts in floating windows on Windows 11.
        @JvmField
        var left = -1

        @JvmField
        var right = -1

        @JvmField
        var top = -1

        @JvmField
        var bottom = -1
    }

    companion object {
        val instance: WindowsDwmApi by lazy { Native.load("dwmapi", WindowsDwmApi::class.java) }
    }
}

