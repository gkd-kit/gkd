package li.gkd.app.subscription

class SubscriptionParseException(
    val path: String,
    val detail: String,
    cause: Throwable? = null,
) : IllegalArgumentException("$path: $detail", cause)
