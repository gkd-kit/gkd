package li.gkd.app.matching.diagnostics

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AccessibilitySettingRecord(
    val notifiedAt: Long,
    val readAt: Long,
    val trigger: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val userId: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val value: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val sourcePackage: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val error: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val previousValue: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val previousSourcePackage: String? = null,
)

/** Session-local comparison; failed reads never replace the last successful observation. */
class AccessibilitySettingChanges {
    private val previous = mutableMapOf<Pair<Int?, String?>, AccessibilitySettingRecord>()

    fun accept(record: AccessibilitySettingRecord): AccessibilitySettingRecord? {
        if (record.error != null) return record
        val before = previous.put(record.userId to record.name, record) ?: return record
        if (before.value == record.value && before.sourcePackage == record.sourcePackage)
            return null
        return record.copy(
            previousValue = before.value,
            previousSourcePackage = before.sourcePackage,
        )
    }
}

/** Parse only the two secure settings, retaining user context and source attribution. */
fun parseAccessibilitySettingsDump(
    lines: Sequence<String>,
    notifiedAt: Long,
    readAt: Long,
    trigger: String,
): List<AccessibilitySettingRecord> {
    var userId: Int? = null
    val records = mutableListOf<AccessibilitySettingRecord>()
    for (line in lines) {
        if (line.startsWith("Permission Denial:")) throw SecurityException(line)
        if (line.contains(" SETTINGS (user ")) {
            userId =
                if (line.startsWith("SECURE SETTINGS (user "))
                    line.substringAfter("(user ").substringBefore(')').toIntOrNull()
                else null
        }
        if (userId == null) continue
        val name = line.substringAfter(" name:", "").substringBefore(' ')
        if (name != "enabled_accessibility_services" && name != "accessibility_enabled") continue
        val valuePart = line.substringAfter(" value:", "")
        val value =
            valuePart
                .substringBefore(" default:")
                .substringBefore(" tag:")
                .removeSuffix(" notPreservedInRestore")
        val source =
            line.substringBefore(" value:").substringAfter(" pkg:", "").takeIf { it.isNotEmpty() }
        records.add(
            AccessibilitySettingRecord(
                notifiedAt,
                readAt,
                trigger,
                userId,
                name,
                value,
                source,
            ),
        )
    }
    check(records.isNotEmpty()) { "Accessibility settings are absent from settings dump" }
    return records
}
