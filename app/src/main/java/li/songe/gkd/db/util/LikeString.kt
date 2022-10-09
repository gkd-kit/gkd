package li.songe.gkd.db.util

import android.database.DatabaseUtils


data class  LikeString (val sqlString: String = "") {
    fun one() = LikeString("$sqlString?")
    fun any() = LikeString("$sqlString*")
    infix fun one(s: String) = LikeString("${sqlString}_").str(s)
    infix fun any(s: String) = LikeString("$sqlString%").str(s)
    infix fun str(s: String) = LikeString(
        sqlString + s.replace("\\", "\\\\")
            .replace("_", "\\_")
            .replace("%", "\\%")
    )

    fun stringify() = "${DatabaseUtils.sqlEscapeString(sqlString)} ESCAPE '\\'"

    companion object {
        fun likeString(value: String = "") = LikeString().str(value)
    }
}