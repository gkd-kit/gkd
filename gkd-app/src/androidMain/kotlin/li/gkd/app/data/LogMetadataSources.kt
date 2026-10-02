package li.gkd.app.data

import kotlinx.serialization.json.Json
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.permission.PermissionStates
import li.gkd.app.util.JsonUtils

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
        "permission.txt" to {
            val permissions = PermissionStates.all.map { it.name to it.value }
            buildString {
                val granted = permissions.filter { it.second }
                if (granted.isNotEmpty()) append("已授权\n" + granted.joinToString("\n") { it.first } + "\n\n")
                val denied = permissions.filterNot { it.second }
                if (denied.isNotEmpty()) append("未授权\n" + denied.joinToString("\n") { it.first } + "\n\n")
                if ((AppInfoRepository.snapshot?.queryPackagesAbnormal == true)) append("其它\n读取应用列表权限异常")
                append("\n")
            }
        },
        "gkd.json" to { metadataJson.encodeToString(META) },
    )
}
