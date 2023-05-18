package li.songe.gkd.db.util

import li.songe.gkd.db.table.SubsConfig
import li.songe.gkd.db.table.SubsItem
import li.songe.gkd.db.table.TriggerLog
import kotlin.reflect.KClass

object RoomAnnotation {

    fun getTableName(cls: KClass<*>): String = when (cls) {
        SubsConfig::class -> "subs_config"
        SubsItem::class -> "subs_item"
        TriggerLog::class -> "trigger_log"
        else -> throw Exception("""not found className : ${cls.qualifiedName}""")
    }

    fun getColumnName(cls: KClass<*>, propertyName: String): String = when (cls) {
        SubsConfig::class -> when (propertyName) {
            SubsConfig::id.name -> "id"
            SubsConfig::ctime.name -> "ctime"
            SubsConfig::mtime.name -> "mtime"
            SubsConfig::type.name -> "type"
            SubsConfig::enable.name -> "enable"
            SubsConfig::subsItemId.name -> "subs_item_id"
            SubsConfig::appId.name -> "app_id"
            SubsConfig::groupKey.name -> "group_key"
            SubsConfig::ruleKey.name -> "rule_key"
            else -> error("""not found columnName : ${cls.qualifiedName}#$propertyName""")
        }

        SubsItem::class -> when (propertyName) {
            SubsItem::id.name -> "id"
            SubsItem::ctime.name -> "ctime"
            SubsItem::mtime.name -> "mtime"
            SubsItem::enable.name -> "enable"
            SubsItem::name.name -> "name"
            SubsItem::updateUrl.name -> "update_url"
            SubsItem::filePath.name -> "file_path"
            SubsItem::index.name -> "index"
            else -> error("""not found columnName : ${cls.qualifiedName}#$propertyName""")
        }

        TriggerLog::class -> when (propertyName) {
            TriggerLog::id.name -> "id"
            TriggerLog::ctime.name -> "ctime"
            TriggerLog::mtime.name -> "mtime"
            TriggerLog::appId.name -> "app_id"
            TriggerLog::activityId.name -> "activity_id"
            TriggerLog::selector.name -> "selector"
            else -> error("""not found columnName : ${cls.qualifiedName}#$propertyName""")
        }

        else -> error("""not found className : ${cls.qualifiedName}""")
    }

}