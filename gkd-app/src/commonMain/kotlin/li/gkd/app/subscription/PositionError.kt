package li.gkd.app.subscription

import kotlinx.serialization.Serializable
import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage
import li.gkd.app.resources.*

/** Serializable coordinate failure shared by validation, action results and diagnostics. */
@Serializable
data class PositionError(
    val field: String,
    val code: String,
    val source: String? = null,
    val startIndex: Int? = null,
    val endIndex: Int? = null,
    val detail: String? = null,
    val inputs: Map<String, Double>? = null,
) {
    val description: String
        get() = buildString {
            append(this@PositionError.field)
            source?.let { append(": ").append(it) }
            append(" — ").append(code)
            if (startIndex != null && endIndex != null)
                append(" [").append(startIndex).append(", ").append(endIndex).append(')')
            detail?.let { append(": ").append(it) }
        }

    val message: ErrorMessage
        get() =
            ErrorMessage.Resource(
                Res.string.rule_invalid_position,
                listOf(ErrorMessage.Text(description)),
            )

    fun at(parent: String): PositionError = copy(field = "$parent.$field")
}

class PositionCalculationException(val error: PositionError, cause: Throwable? = null) :
    IllegalArgumentException(error.code, cause), BusinessError {
    override val errorCode: String
        get() = error.code

    override val errorPath: String
        get() = error.field

    override val errorMessage: ErrorMessage
        get() = error.message
}
