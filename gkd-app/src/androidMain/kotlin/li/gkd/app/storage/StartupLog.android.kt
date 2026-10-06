package li.gkd.app.storage

import android.app.Application
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.AndroidStorage
import java.io.File
import java.io.IOException
import java.util.TimeZone

private val diagnosticJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

fun storageExportMetadata(): String {
    return diagnosticJson.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("capturedAt", System.currentTimeMillis())
        put("timeZone", TimeZone.getDefault().id)
        put("startedAt", app.startTime)
        put("selectionAtStartup", diagnosticJson.encodeToJsonElement(DirectorySelectionRecord.serializer(), AndroidStorage.directorySelectionRecord))
        put("internal", app.filesDir.absolutePath)
        put("markerNow", app.filesDir.resolve(".gkd").isFile)
        put("externalDiagnosticOnly", externalDirectoryInfo(app.getExternalFilesDir(null)))
    })
}

/** Runs during storage selection, before the database or settings create any data. */
fun recordStartupLog(
    directorySelectionRecord: DirectorySelectionRecord,
    internalFiles: File,
    external: File?,
) {
    val record = buildJsonObject {
        put("startedAt", app.startTime)
        put("capturedAt", System.currentTimeMillis())
        put("timeZone", TimeZone.getDefault().id)
        put("uptimeMillis", SystemClock.elapsedRealtime())
        put("pid", Process.myPid())
        put("uid", Process.myUid())
        if (AndroidTarget.P) put("processName", Application.getProcessName())
        put("packageName", app.packageName)
        put("selection", diagnosticJson.encodeToJsonElement(DirectorySelectionRecord.serializer(), directorySelectionRecord))
        put("app", buildJsonObject {
            put("versionName", META.versionName)
            put("versionCode", META.versionCode)
            put("channel", META.channel)
            put("commitId", META.commitId)
        })
        put("device", buildJsonObject {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("release", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("fingerprint", Build.FINGERPRINT)
        })
        put("systemStorage", buildJsonObject {
            put("externalState", Environment.getExternalStorageState())
            put("userUnlocked", app.getSystemService(UserManager::class.java).isUserUnlocked)
        })
        put("markerAfter", internalFiles.resolve(".gkd").isFile)
        put("internal", internalFiles.absolutePath)
        // When a marker bypasses selection, this observation must not be mistaken for its input.
        put(if (!directorySelectionRecord.markerBefore) "external" else "externalDiagnosticOnly", run {
            val observed = if (!directorySelectionRecord.markerBefore) external else app.getExternalFilesDir(null)
            externalDirectoryInfo(observed)
        })
    }
    try {
        // Do not access AndroidStorage or LogUtils here: both depend on this lazy initialization.
        StartupLogs.write(
            internalFiles.resolve("startup-log"), app.startTime,
            diagnosticJson.encodeToString(JsonObject.serializer(), record),
        )
    } catch (e: IOException) {
        Log.e("GKD", "Cannot write startup diagnostic log", e)
    }
}

private fun externalDirectoryInfo(directory: File?) = buildJsonObject {
    put("path", directory?.absolutePath)
    put("state", if (directory == null) Environment.getExternalStorageState() else Environment.getExternalStorageState(directory))
}
