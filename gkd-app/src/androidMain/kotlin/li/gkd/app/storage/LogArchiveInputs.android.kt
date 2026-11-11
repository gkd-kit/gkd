package li.gkd.app.storage

import java.io.File
import java.io.IOException
import li.gkd.app.data.LogMetadataSources
import li.gkd.app.logging.LogUtils
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeStartupException

actual fun logArchiveInputs() =
    LogArchiveInputs(
        LogMetadataSources.sources(),
        privilegeCrashFiles = listPrivilegeCrashFiles(),
    )

private fun listPrivilegeCrashFiles(): List<File> =
    try {
        Privilege.listFallbackCrashLogFiles()
    } catch (e: IOException) {
        LogUtils.w("Unable to list privilege crash reports", e)
        emptyList()
    } catch (e: PrivilegeStartupException) {
        LogUtils.w("Unable to list privilege crash reports", e)
        emptyList()
    }
