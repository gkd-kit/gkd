package li.gkd.app.storage

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.util.JsonUtils
import li.gkd.db.DatabaseLogSnapshot

private val databaseLogJson = Json(JsonUtils.keepingNulls) { prettyPrint = true }

@Serializable
private data class DatabaseLogManifest(
    val formatVersion: Int = 1,
    val databaseVersion: Int,
    val exportedAt: Long,
    val tables: Map<String, Int>,
)

fun DatabaseLogSnapshot.writeJsonFiles(directory: File) {
    Files.createDirectory(directory.toPath())
    val counts = linkedMapOf<String, Int>()
    counts["subs_item"] = directory.writeTable("subs_item", subsItems)
    counts["subs_app_config"] = directory.writeTable("subs_app_config", appConfigs)
    counts["subs_category_config"] = directory.writeTable("subs_category_config", categoryConfigs)
    counts["subs_app_group_config"] = directory.writeTable("subs_app_group_config", appGroupConfigs)
    counts["subs_global_group_config"] =
        directory.writeTable("subs_global_group_config", globalGroupConfigs)
    counts["snapshot"] = directory.writeTable("snapshot", snapshots)
    counts["action_log"] = directory.writeTable("action_log", actionLogs)
    counts["rule_trigger_stats"] = directory.writeTable("rule_trigger_stats", ruleTriggerStats)
    counts["activity_log"] = directory.writeTable("activity_log", activityLogs)
    counts["app_last_visit"] = directory.writeTable("app_last_visit", appLastVisits)
    counts["a11y_event_log"] = directory.writeTable("a11y_event_log", a11yEventLogs)
    counts["match_diagnostic"] = directory.writeTable("match_diagnostic", matchDiagnostics)
    directory
        .resolve("manifest.json")
        .writeText(
            databaseLogJson.encodeToString(
                DatabaseLogManifest(
                    databaseVersion = databaseVersion,
                    exportedAt = exportedAt,
                    tables = counts,
                ),
            ),
        )
}

private inline fun <reified T> File.writeTable(name: String, rows: List<T>): Int {
    resolve("$name.json").writeText(databaseLogJson.encodeToString(rows))
    return rows.size
}
