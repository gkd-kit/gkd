package li.songe.gkd.debug

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import li.songe.gkd.App
import li.songe.gkd.accessibility.GkdAbService
import li.songe.gkd.debug.server.RpcError
import li.songe.gkd.debug.server.api.Snapshot
import li.songe.gkd.debug.server.api.Window
import li.songe.gkd.util.Ext.getApplicationInfoExt
import li.songe.gkd.util.Singleton
import java.io.File

object Ext {
    val snapshotDir by lazy {
        App.context.getExternalFilesDir("server-snapshot")!!.apply { if (!exists()) mkdir() }
    }
    val windowDir by lazy {
        App.context.getExternalFilesDir("server-window")!!.apply { if (!exists()) mkdir() }
    }
    val screenshotDir by lazy {
        App.context.getExternalFilesDir("server-screenshot")!!.apply { if (!exists()) mkdir() }
    }

    suspend fun captureSnapshot(): Snapshot {
        if (!GkdAbService.isRunning()) {
            throw RpcError("无障碍不可用")
        }
        val packageManager = App.context.packageManager
        val windowInfo = Window.singleton
        val bitmap = ScreenshotService.screenshot() ?: throw RpcError("截屏不可用")
        val snapshot = Snapshot(
            appId = windowInfo.appId ?: "",
            activityId = windowInfo.activityId ?: "",
            appName = windowInfo.appId?.let { appId ->
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfoExt(
                        appId
                    )
                ).toString()
            } ?: "",
        )

        withContext(Dispatchers.IO) {
            File(windowDir.absolutePath + "/${snapshot.id}.json").writeText(
                Singleton.json.encodeToString(windowInfo)
            )
            val stream =
                File(screenshotDir.absolutePath + "/${snapshot.id}.png").outputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            File(snapshotDir.absolutePath + "/${snapshot.id}.json").writeText(
                Singleton.json.encodeToString(snapshot)
            )
        }
        return snapshot
    }
}