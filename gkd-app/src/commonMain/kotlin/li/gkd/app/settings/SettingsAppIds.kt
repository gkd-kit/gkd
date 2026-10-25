package li.gkd.app.settings

import li.gkd.app.model.AppInfo
import li.gkd.app.model.isValidAppId

object SettingsAppIds {
    fun decode(text: String): Set<String> = text.lineSequence().filter(String::isValidAppId).toSet()

    fun decodeBackup(text: String): Set<String> = text.lineSequence().filterNot {
        it.isBlank() || it.startsWith('#')
    }.onEach { require(it.isValidAppId()) { "Invalid app ID in settings backup: $it" } }.toSet()

    fun encode(ids: Set<String>): String = ids.sorted().joinToString("\n")

    fun format(ids: Set<String>, apps: Map<String, AppInfo>): String {
        return ids.sorted().sortedBy { if (it in apps) 0 else 1 }
            .joinToString("\n\n", postfix = "\n\n") { id ->
                apps[id]?.let { "$id\n# ${it.name}" } ?: id
            }
    }
}
