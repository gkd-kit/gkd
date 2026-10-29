package li.gkd.app.priv.shizuku

import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import androidx.annotation.Keep
import kotlin.system.exitProcess
import li.gkd.aidl.IPrivilegeShizukuStartService
import priv.kit.core.PrivilegeExternalStartupHost

class PrivilegeShizukuStartService @Keep constructor() : IPrivilegeShizukuStartService.Stub() {
    private val host = PrivilegeExternalStartupHost()

    override fun start(
        commandLine: String,
        stdout: ParcelFileDescriptor,
        stderr: ParcelFileDescriptor,
        resultReceiver: ResultReceiver,
    ) {
        host.start(
            commandLine = commandLine,
            stdout = stdout,
            stderr = stderr,
            resultReceiver = resultReceiver,
        )
    }

    override fun destroy() {
        host.close()
        exitProcess(0)
    }
}
