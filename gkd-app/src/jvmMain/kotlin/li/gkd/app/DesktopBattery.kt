package li.gkd.app

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary

object DesktopBattery {
    private interface PowerApi : StdCallLibrary {
        fun GetSystemPowerStatus(status: Pointer): Int
    }

    private val api by lazy { Native.load("kernel32", PowerApi::class.java) }

    fun readPercent(): Int {
        if (!Platform.isWindows()) return 100
        return try {
            // SYSTEM_POWER_STATUS: four BYTE fields followed by two DWORD fields.
            Memory(12).use { status ->
                if (api.GetSystemPowerStatus(status) == 0) 100
                else (status.getByte(2).toInt() and 0xff).takeIf { it in 0..100 } ?: 100
            }
        } catch (_: Exception) {
            100
        } catch (_: LinkageError) {
            100
        }
    }
}
