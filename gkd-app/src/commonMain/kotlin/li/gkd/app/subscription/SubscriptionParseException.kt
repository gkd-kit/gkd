package li.gkd.app.subscription

import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage
import li.gkd.app.resources.*

class SubscriptionParseException(
    val path: String,
    val detail: String,
    cause: Throwable? = null,
) : IllegalArgumentException("$path: $detail", cause), BusinessError {
    override val errorCode: String get() = "INVALID_SUBSCRIPTION"
    override val errorPath: String get() = path
    override val errorMessage: ErrorMessage
        get() = ErrorMessage.WithDetail(
            ErrorMessage.Resource(Res.string.rpc_invalid_subscription), ErrorMessage.Text(detail), ": ",
        )
}
