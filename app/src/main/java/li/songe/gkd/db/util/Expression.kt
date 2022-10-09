package li.songe.gkd.db.util

import android.database.DatabaseUtils
import kotlin.reflect.KClass

data class Expression<L : Any, R, T : Any>(
    val left: L,
    val operator: String,
    val right: R,
    val tableClass: KClass<T>
) {
    fun stringify(): String {
        val nameText = when (left) {
            is String -> left.toString()
            is Expression<*, *, *> -> left.stringify()
            else -> throw Exception("not support type : $left")
        }
        val valueText = when (right) {
            null -> "NULL"
            is Boolean -> (if (right) 0 else 1).toString()
            is String -> DatabaseUtils.sqlEscapeString(right.toString())
            is Byte, is UByte, is Short, is UShort, is Int, is UInt, is Long, is ULong, is Float, is Double -> right.toString()
            is List<*> -> "(" + right.joinToString(",\u0020") {
                if (it is String) {
                    DatabaseUtils.sqlEscapeString(it)
                } else {
                    it?.toString() ?: "NULL"
                }
            } + ")"
            is GlobString -> right.stringify()
            is LikeString -> right.stringify()
            is Expression<*, *, *> -> "(${right.stringify()})"
            else -> throw Exception("not support type : $right")
        }
        return "$nameText $operator $valueText"
    }

    infix fun <L2 : Any, R2> and(other: Expression<L2, R2, T>) =
        Expression(this, "AND", other, tableClass)

    infix fun <L2 : Any, R2> or(other: Expression<L2, R2, T>) =
        Expression(this, "OR", other, tableClass)

}