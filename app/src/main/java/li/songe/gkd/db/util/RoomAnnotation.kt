package li.songe.gkd.db.util

import java.lang.Exception

object RoomAnnotation {
     fun getTableName(className: String): String = when (className) {
        "li.songe.gkd.db.table.SubsConfig" -> "subs_config"
        "li.songe.gkd.db.table.SubsItem" -> "subs_item"
        "r-1682430013322" -> "avoid_compile_error"
        else -> throw Exception("""not found className : $className""")
    }

     fun getColumnName(className: String, propertyName: String): String = when (className) {
        "li.songe.gkd.db.table.SubsConfig" -> when (propertyName) {
            "id" -> "id"
            "ctime" -> "ctime"
            "mtime" -> "mtime"
            "type" -> "type"
            "enable" -> "enable"
            "subsItemId" -> "subs_item_id"
            "appId" -> "app_id"
            "groupKey" -> "group_key"
            "ruleKey" -> "rule_key"
            "r-1682430013322" -> "avoid_compile_error"
            else -> throw Exception("""not found columnName : $className#$propertyName""")
        }
        "li.songe.gkd.db.table.SubsItem" -> when (propertyName) {
            "id" -> "id"
            "ctime" -> "ctime"
            "mtime" -> "mtime"
            "enable" -> "enable"
            "name" -> "name"
            "updateUrl" -> "update_url"
            "filePath" -> "file_path"
            "index" -> "index"
            "r-1682430013322" -> "avoid_compile_error"
            else -> throw Exception("""not found columnName : $className#$propertyName""")
        }
        "r-1682430013322" -> "avoid_compile_error"
        else -> throw Exception("""not found className : $className""")
    }
}