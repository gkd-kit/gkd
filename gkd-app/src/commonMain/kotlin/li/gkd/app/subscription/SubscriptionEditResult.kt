package li.gkd.app.subscription

import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage

/** Expected input and edit failures carry diagnostics without constructing a Throwable. */
sealed interface SubscriptionEditResult<out T> {
    data class Success<T>(val value: T) : SubscriptionEditResult<T>

    data class Rejected(
        val reason: SubscriptionFailureReason,
        val arguments: List<String> = emptyList(),
        override val errorPath: String? = null,
        override val errorCode: String = reason.name,
        override val errorMessage: ErrorMessage =
            ErrorMessage.Resource(
                reason.messageResource,
                arguments.map { ErrorMessage.Text(it) },
            ),
    ) : SubscriptionEditResult<Nothing>, BusinessError {
        constructor(
            reason: SubscriptionFailureReason,
            detail: ErrorMessage,
            arguments: List<String> = emptyList(),
            errorPath: String? = null,
            errorCode: String = reason.name,
        ) : this(
            reason,
            arguments,
            errorPath,
            errorCode,
            subscriptionFailureMessage(reason, arguments, detail),
        )
    }

    fun <R> map(transform: (T) -> R): SubscriptionEditResult<R> =
        when (this) {
            is SubscriptionEditResult.Success -> SubscriptionEditResult.Success(transform(value))
            is SubscriptionEditResult.Rejected -> this
        }

    fun <R> flatMap(transform: (T) -> SubscriptionEditResult<R>): SubscriptionEditResult<R> =
        when (this) {
            is SubscriptionEditResult.Success -> transform(value)
            is SubscriptionEditResult.Rejected -> this
        }
}
