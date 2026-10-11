package li.gkd.app.error

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** A business failure that can be displayed without exposing its diagnostic stack. */
interface BusinessError {
    val errorCode: String
    val errorMessage: ErrorMessage
    val errorPath: String? get() = null
}

/** Resources and message parameters are resolved at the output boundary. */
sealed interface ErrorMessage {
    suspend fun resolve(): String

    data class Text(val value: String) : ErrorMessage {
        override suspend fun resolve(): String = value
    }
    data class Resource(
        val resource: StringResource,
        val arguments: List<ErrorMessage> = emptyList(),
    ) : ErrorMessage {
        override suspend fun resolve(): String = getString(
            resource, *arguments.map { it.resolve() }.toTypedArray(),
        )
    }

    data class WithDetail(
        val message: ErrorMessage,
        val detail: ErrorMessage,
        val separator: String = "\n",
    ) : ErrorMessage {
        fun format(message: String, detail: String): String =
            if (detail.isBlank()) message else message + separator + detail

        override suspend fun resolve(): String = format(message.resolve(), detail.resolve())
    }
}
