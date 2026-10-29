package li.gkd.app.settings

import li.gkd.app.model.AppInfo
import li.gkd.app.model.isValidAppId

object SettingsAppIds {
    fun decode(text: String): Set<String> = text.lineSequence().filter(String::isValidAppId).toSet()

    // 豁免：备份中的非法应用 ID 直接忽略，不因单行错误拒绝恢复其余有效条目。
    fun decodeBackup(text: String): Set<String> =
        text
            .lineSequence()
            .filterNot {
                it.isBlank() || it.startsWith('#')
            }
            .filter(String::isValidAppId)
            .toSet()

    fun encode(ids: Set<String>): String = ids.sorted().joinToString("\n")

    fun format(ids: Set<String>, apps: Map<String, AppInfo>): String {
        return ids.sorted()
            .sortedBy { if (it in apps) 0 else 1 }
            .joinToString("\n\n", postfix = "\n\n") { id ->
                apps[id]?.let { "$id\n# ${it.name}" } ?: id
            }
    }
}
