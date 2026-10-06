package li.gkd.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.permission.PermissionStates
import li.gkd.app.util.JsonUtils
import li.gkd.app.storage.storageExportMetadata
import java.util.TimeZone

/** Host facts only; shared LogArchive owns material creation, archiving and cleanup. */
object LogMetadataSources {
    private val metadataJson = Json(from = JsonUtils.default) { prettyPrint = true }

    fun sources(): Map<String, () -> String> = mapOf(
        "source-paths.txt" to {
            app.assets.open("source-paths.txt").bufferedReader().use { it.readText() }
        },
        "apps.json" to {
            JsonUtils.default.encodeToString(requireNotNull(AppInfoRepository.snapshot).inventory)
        },
        "permission.json" to ::permissionMetadata,
        "storage.json" to ::storageExportMetadata,
        "gkd.json" to { metadataJson.encodeToString(META) },
    )

    private fun permissionMetadata(): String = metadataJson.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("capturedAt", System.currentTimeMillis())
        put("timeZone", TimeZone.getDefault().id)
        // These are application checks (including version exemptions), not raw system grants.
        put("source", "cached_application_check")
        put("permissions", buildJsonArray {
            PermissionStates.all.forEach { permission ->
                val check = permission.lastCheck
                add(buildJsonObject {
                    put("id", permission.id)
                    put("name", permission.name)
                    put("checkedAt", check?.checkedAt)
                    put("status", when {
                        check == null -> "unknown"
                        check.granted -> "granted"
                        else -> "denied"
                    })
                })
            }
        })
        put("appList", buildJsonObject {
            put("source", "cached_app_catalog")
            put("queryAbnormal", AppInfoRepository.snapshot?.queryPackagesAbnormal)
        })
    })
}
