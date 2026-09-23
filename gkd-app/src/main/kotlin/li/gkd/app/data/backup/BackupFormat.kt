package li.gkd.app.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.text.UiStrings
import li.gkd.db.SubsAppConfig
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.RuleGroupType
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot

// Archive versions are independent of Room schema versions.
@Serializable
data class BackupDatabaseData(
    val formatVersion: Int = 2,
    val subsItems: List<BackupSubsItem> = emptyList(),
    val appConfigs: List<BackupAppConfig> = emptyList(),
    val categoryConfigs: List<BackupCategoryConfig> = emptyList(),
    val appGroupConfigs: List<BackupAppGroupConfig> = emptyList(),
    val globalGroupConfigs: List<BackupGlobalGroupConfig> = emptyList(),
) {
    fun toSnapshot() = SubscriptionConfigSnapshot(
        subsItems = subsItems.map { it.toEntity() },
        appConfigs = appConfigs.map { it.toEntity() },
        categoryConfigs = categoryConfigs.map { it.toEntity() },
        appGroupConfigs = appGroupConfigs.map { it.toEntity() },
        globalGroupConfigs = globalGroupConfigs.map { it.toEntity() },
    )

    companion object {
        fun fromSnapshot(snapshot: SubscriptionConfigSnapshot) = BackupDatabaseData(
            subsItems = snapshot.subsItems.map(BackupSubsItem::fromEntity),
            appConfigs = snapshot.appConfigs.map(BackupAppConfig::fromEntity),
            categoryConfigs = snapshot.categoryConfigs.map(BackupCategoryConfig::fromEntity),
            appGroupConfigs = snapshot.appGroupConfigs.map(BackupAppGroupConfig::fromEntity),
            globalGroupConfigs = snapshot.globalGroupConfigs.map(BackupGlobalGroupConfig::fromEntity),
        )
    }
}

@Serializable
data class BackupSubsItem(
    val id: Long,
    val ctime: Long,
    val mtime: Long,
    val enable: Boolean = false,
    val enableUpdate: Boolean = true,
    val order: Int,
    val updateUrl: String? = null,
) {
    fun toEntity() = SubsItem(
        id = id,
        ctime = ctime,
        mtime = mtime,
        enable = enable,
        enableUpdate = enableUpdate,
        order = order,
        updateUrl = updateUrl,
    )

    companion object {
        fun fromEntity(entity: SubsItem) = BackupSubsItem(
            id = entity.id,
            ctime = entity.ctime,
            mtime = entity.mtime,
            enable = entity.enable,
            enableUpdate = entity.enableUpdate,
            order = entity.order,
            updateUrl = entity.updateUrl,
        )
    }
}

@Serializable
data class BackupAppConfig(
    val subsId: Long,
    val appId: String,
    val enable: Boolean,
) {
    fun toEntity() = SubsAppConfig(
        subsId = subsId,
        appId = appId,
        enable = enable,
    )

    companion object {
        fun fromEntity(entity: SubsAppConfig) = BackupAppConfig(
            subsId = entity.subsId,
            appId = entity.appId,
            enable = entity.enable,
        )
    }
}

@Serializable
data class BackupCategoryConfig(
    val subsId: Long,
    val categoryKey: Int,
    val enable: Boolean? = null,
) {
    fun toEntity() = SubsCategoryConfig(
        subsId = subsId,
        categoryKey = categoryKey,
        enable = enable,
    )

    companion object {
        fun fromEntity(entity: SubsCategoryConfig) = BackupCategoryConfig(
            subsId = entity.subsId,
            categoryKey = entity.categoryKey,
            enable = entity.enable,
        )
    }
}

@Serializable
data class BackupAppGroupConfig(
    val subsId: Long,
    val appId: String,
    val groupKey: Int,
    val enable: Boolean? = null,
    val exclude: String = "",
) {
    fun toEntity() = SubsAppGroupConfig(
        subsId = subsId,
        appId = appId,
        groupKey = groupKey,
        enable = enable,
        exclude = exclude,
    )

    companion object {
        fun fromEntity(entity: SubsAppGroupConfig) = BackupAppGroupConfig(
            subsId = entity.subsId,
            appId = entity.appId,
            groupKey = entity.groupKey,
            enable = entity.enable,
            exclude = entity.exclude,
        )
    }
}

@Serializable
data class BackupGlobalGroupConfig(
    val subsId: Long,
    val groupKey: Int,
    val enable: Boolean? = null,
    val exclude: String = "",
) {
    fun toEntity() = SubsGlobalGroupConfig(
        subsId = subsId,
        groupKey = groupKey,
        enable = enable,
        exclude = exclude,
    )

    companion object {
        fun fromEntity(entity: SubsGlobalGroupConfig) = BackupGlobalGroupConfig(
            subsId = entity.subsId,
            groupKey = entity.groupKey,
            enable = entity.enable,
            exclude = entity.exclude,
        )
    }
}

object BackupFormat {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(data: BackupDatabaseData): String = json.encodeToString(data)

    fun decode(text: String): BackupDatabaseData {
        val root = json.parseToJsonElement(text).jsonObject
        return when (val version = root["formatVersion"]?.jsonPrimitive?.int ?: 1) {
            1 -> json.decodeFromJsonElement(LegacyBackupV1.serializer(), root).convert()
            2 -> json.decodeFromJsonElement(BackupDatabaseData.serializer(), root)
            else -> error(UiStrings.backup_version_unsupported(version))
        }
    }
}

@Serializable
private data class LegacyBackupV1(
    val subsItems: List<BackupSubsItem>? = null,
    val subsConfigs: List<LegacyGroupConfig>? = null,
    val appConfigs: List<LegacyAppConfig>? = null,
    val categoryConfigs: List<LegacyCategoryConfig>? = null,
) {
    fun convert(): BackupDatabaseData {
        val groups = subsConfigs.orEmpty().sortedBy { it.id }
        require(groups.all { it.type == RuleGroupType.App || it.type == RuleGroupType.Global }) {
            UiStrings.backup_unknown_rule_config_type
        }
        return BackupDatabaseData(
            subsItems = subsItems.orEmpty(),
            appGroupConfigs = groups.filter { it.type == RuleGroupType.App }
                .distinctBy { Triple(it.subsId, it.appId, it.groupKey) }
                .map { BackupAppGroupConfig(it.subsId, it.appId, it.groupKey, it.enable, it.exclude) },
            globalGroupConfigs = groups.filter { it.type == RuleGroupType.Global }
                .distinctBy { it.subsId to it.groupKey }
                .map { BackupGlobalGroupConfig(it.subsId, it.groupKey, it.enable, it.exclude) },
            appConfigs = appConfigs.orEmpty().sortedBy { it.id }
                .distinctBy { it.subsId to it.appId }
                .map { BackupAppConfig(it.subsId, it.appId, it.enable) },
            categoryConfigs = categoryConfigs.orEmpty().sortedBy { it.id }
                .distinctBy { it.subsId to it.categoryKey }
                .map { BackupCategoryConfig(it.subsId, it.categoryKey, it.enable) },
        )
    }
}

@Serializable
private data class LegacyGroupConfig(
    val id: Long,
    val type: Int,
    val subsId: Long,
    val appId: String = "",
    val groupKey: Int = -1,
    val enable: Boolean? = null,
    val exclude: String = "",
)

@Serializable
private data class LegacyAppConfig(
    val id: Long,
    val subsId: Long,
    val appId: String,
    val enable: Boolean,
)

@Serializable
private data class LegacyCategoryConfig(
    val id: Long,
    val subsId: Long,
    val categoryKey: Int,
    val enable: Boolean? = null,
)
