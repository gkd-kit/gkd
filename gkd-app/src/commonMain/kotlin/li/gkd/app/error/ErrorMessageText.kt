package li.gkd.app.error

import androidx.compose.runtime.Composable
import li.gkd.app.subscription.SubscriptionPersistence
import org.jetbrains.compose.resources.stringResource

@Composable
fun ErrorMessage.messageResource(): String = when (this) {
    is ErrorMessage.Text -> value
    is ErrorMessage.Resource -> stringResource(
        resource,
        *arguments.map { it.messageResource() }.toTypedArray(),
    )
    is ErrorMessage.WithDetail -> format(message.messageResource(), detail.messageResource())
}

/** Resolves business errors at the rendering boundary, including persistence wrappers. */
@Composable
fun Throwable.messageResource(): String = when (this) {
    is BusinessError -> errorMessage.messageResource()
    is SubscriptionPersistence.DeleteException -> cause?.messageResource() ?: toString()
    else -> message ?: toString()
}
