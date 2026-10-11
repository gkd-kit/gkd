package li.gkd.app.error

import kotlinx.coroutines.runBlocking
import li.gkd.app.subscription.SubscriptionPersistence

/** Resolves business errors at the output boundary, including persistence wrappers. */
suspend fun Throwable.resolveMessage(): String = when (this) {
    is BusinessError -> errorMessage.resolve()
    is SubscriptionPersistence.DeleteException -> cause?.resolveMessage() ?: toString()
    else -> message ?: toString()
}

/** Resolves resources only at existing synchronous feedback boundaries. */
fun Throwable.displayMessage(): String = runBlocking { resolveMessage() }
