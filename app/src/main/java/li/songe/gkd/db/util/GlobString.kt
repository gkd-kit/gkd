package li.songe.gkd.db.util

import android.database.DatabaseUtils

data class GlobString(val sqlString: String = "") {
    fun one() = GlobString("$sqlString?")
    fun any() = GlobString("$sqlString*")
    infix fun one(s: String) = GlobString("$sqlString?").str(s)
    infix fun any(s: String) = GlobString("$sqlString*").str(s)
    infix fun str(s: String) = GlobString(
        sqlString + s.replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("?", "\\?")
    )

    fun stringify() = "${DatabaseUtils.sqlEscapeString(sqlString)} ESCAPE '\\'"

    companion object {
        fun globString(value: String = "") = GlobString().str(value)
    }
}

