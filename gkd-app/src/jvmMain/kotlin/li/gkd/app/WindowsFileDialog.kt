package li.gkd.app

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.W32Errors
import com.sun.jna.platform.win32.WTypes.CLSCTX_INPROC_SERVER
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinError.ERROR_CANCELLED
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.awt.Window
import java.io.File
import java.util.concurrent.Executors

/** Windows Common Item Dialog; all COM objects stay on one dedicated STA thread. */
object WindowsFileDialog {
    // Windows SDK COM identifiers: CLSID selects the object, IID selects its interface.
    private const val CLSID_FILE_OPEN_DIALOG = "{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}"
    private const val CLSID_FILE_SAVE_DIALOG = "{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}"
    private const val IID_FILE_DIALOG = "{42F85136-DB7E-439C-85F1-E4075D135FC8}"
    private const val IID_SHELL_ITEM = "{43826D1E-E718-42EE-BC55-A1E261C37BFE}"

    private val HRESULT_CANCELLED = W32Errors.HRESULT_FROM_WIN32(ERROR_CANCELLED).toInt()

    // Windows SDK options not exposed by the current JNA platform library.
    private const val FOS_FORCEFILESYSTEM = 0x40
    private const val FOS_PATHMUSTEXIST = 0x800
    private const val FOS_FILEMUSTEXIST = 0x1000
    private const val FOS_OVERWRITEPROMPT = 0x2
    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()

    // Zero-based COM vtable slots, including inherited methods, from ShObjIdl_core.h.
    private const val FILE_DIALOG_SHOW = 3
    private const val FILE_DIALOG_SET_OPTIONS = 9
    private const val FILE_DIALOG_GET_OPTIONS = 10
    private const val FILE_DIALOG_SET_DEFAULT_FOLDER = 11
    private const val FILE_DIALOG_SET_FILE_NAME = 15
    private const val FILE_DIALOG_GET_RESULT = 20
    private const val FILE_DIALOG_SET_DEFAULT_EXTENSION = 22
    private const val FILE_DIALOG_CLOSE = 23
    private const val SHELL_ITEM_GET_DISPLAY_NAME = 5

    private const val CANCELLATION_CHECK_INTERVAL_MS = 100

    suspend fun choose(owner: Window, directory: File, save: File?): File? {
        val hwnd = HWND(Native.getWindowPointer(owner))
        val job = currentCoroutineContext()[Job]
        // A fresh thread avoids inheriting an MTA apartment from a shared coroutine worker.
        return Executors.newSingleThreadExecutor { task ->
            Thread(task, "GKD file dialog").apply { isDaemon = true }
        }.asCoroutineDispatcher().use { dispatcher ->
            withContext(dispatcher) {
                val ole = Ole32.INSTANCE
                COMUtils.checkRC(ole.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED))
                try {
                    show(hwnd, directory, save) { job?.isActive == false }
                } finally {
                    ole.CoUninitialize()
                }
            }
        }
    }

    private fun show(owner: HWND, directory: File, save: File?, cancelled: () -> Boolean): File? {
        val result = PointerByReference()
        COMUtils.checkRC(
            Ole32.INSTANCE.CoCreateInstance(
                GUID(if (save == null) CLSID_FILE_OPEN_DIALOG else CLSID_FILE_SAVE_DIALOG),
                null, CLSCTX_INPROC_SERVER,
                GUID(IID_FILE_DIALOG),
                result
            )
        )
        return ComObject(result.value).use { dialog ->
            val options = IntByReference()
            dialog.check(FILE_DIALOG_GET_OPTIONS, options)
            dialog.check(
                FILE_DIALOG_SET_OPTIONS,
                options.value or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST or
                    if (save == null) FOS_FILEMUSTEXIST else FOS_OVERWRITEPROMPT
            )
            if (directory.isDirectory) {
                val folder = PointerByReference()
                val createItem = NativeLibrary.getInstance("shell32")
                    .getFunction("SHCreateItemFromParsingName", Function.ALT_CONVENTION)
                COMUtils.checkRC(HRESULT(createItem.invokeInt(arrayOf(
                    WString(directory.absolutePath), null,
                    GUID(IID_SHELL_ITEM), folder
                ))))
                ComObject(folder.value).use { dialog.check(FILE_DIALOG_SET_DEFAULT_FOLDER, it.pointer) }
            }
            if (save != null) {
                dialog.check(FILE_DIALOG_SET_FILE_NAME, WString(save.name))
                if (save.extension.isNotEmpty()) {
                    dialog.check(FILE_DIALOG_SET_DEFAULT_EXTENSION, WString(save.extension))
                }
            }

            // Show runs a native message loop. Its timer closes on session cancellation or
            // owner disposal, on this same STA; never call the COM pointer from another thread.
            val user = NativeLibrary.getInstance("user32")
            val timerCallback = object : DialogTimer {
                override fun invoke(hwnd: Pointer?, message: Int, timer: Pointer?, time: Int) {
                    if (cancelled() || !User32.INSTANCE.IsWindow(owner)) {
                        dialog.call(FILE_DIALOG_CLOSE, HRESULT_CANCELLED)
                    }
                }
            }
            val timer = user.getFunction("SetTimer", Function.ALT_CONVENTION)
                .invokePointer(arrayOf(null, null, CANCELLATION_CHECK_INTERVAL_MS, timerCallback))
            check(timer != null) { "Unable to create file dialog cancellation timer" }
            val shown = try {
                if (cancelled() || !User32.INSTANCE.IsWindow(owner)) return@use null
                dialog.call(FILE_DIALOG_SHOW, owner)
            } finally {
                user.getFunction("KillTimer", Function.ALT_CONVENTION).invokeInt(arrayOf(null, timer))
                java.lang.ref.Reference.reachabilityFence(timerCallback)
            }
            if (shown == HRESULT_CANCELLED) return@use null
            COMUtils.checkRC(HRESULT(shown))
            val item = PointerByReference()
            dialog.check(FILE_DIALOG_GET_RESULT, item)
            ComObject(item.value).use { selected ->
                val path = PointerByReference()
                selected.check(SHELL_ITEM_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, path)
                try {
                    File(path.value.getWideString(0))
                } finally {
                    Ole32.INSTANCE.CoTaskMemFree(path.value)
                }
            }
        }
    }

    // Public callback method is required by JNA reflection.
    interface DialogTimer : StdCallCallback {
        fun invoke(hwnd: Pointer?, message: Int, timer: Pointer?, time: Int)
    }

    /** Slots follow IFileDialog / IShellItem in the Windows SDK's ShObjIdl_core.h. */
    private class ComObject(pointer: Pointer) : Unknown(pointer), AutoCloseable {
        fun call(slot: Int, vararg args: Any?): Int =
            _invokeNativeInt(slot, arrayOf(pointer, *args))

        fun check(slot: Int, vararg args: Any?) = COMUtils.checkRC(HRESULT(call(slot, *args)))
        override fun close() { Release() }
    }
}
