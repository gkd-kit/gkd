package li.gkd.app.subscription

object CategoryPolicy {
    fun validateEdit(
        subscription: RawSubscription,
        categoryKey: Int?,
        name: String,
    ): SubscriptionFailureReason? {
        if (!subscription.isLocal) {
            return SubscriptionFailureReason.RemoteCategoryEditUnsupported
        }
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return SubscriptionFailureReason.CategoryNameRequired
        }
        if (subscription.categories.any { it.key != categoryKey && it.name == trimmedName }) {
            return SubscriptionFailureReason.CategoryNameDuplicate
        }
        if (categoryKey == null) {
            if ((subscription.categories.maxOfOrNull { it.key } ?: -1) == Int.MAX_VALUE) {
                return SubscriptionFailureReason.CategoryKeyExhausted
            }
        } else {
            if (subscription.categories.none { it.key == categoryKey }) {
                return SubscriptionFailureReason.CategoryMissing
            }
        }
        return null
    }

    fun previewEdit(
        subscription: RawSubscription,
        categoryKey: Int?,
        name: String,
        description: String,
    ): SubscriptionEditResult<RawSubscription> {
        validateEdit(subscription, categoryKey, name)?.let {
            return SubscriptionEditResult.Rejected(it)
        }
        val trimmedName = name.trim()
        val category =
            if (categoryKey == null) {
                val maxKey = subscription.categories.maxOfOrNull { it.key } ?: -1
                RawSubscription.RawCategory(maxKey + 1, trimmedName, null, null)
            } else {
                subscription.categories.first { it.key == categoryKey }
            }
        return SubscriptionEditResult.Success(
            subscription.edit {
                putCategory(
                    category.copy(
                        name = trimmedName,
                        desc = description.trim().ifEmpty { null },
                    ),
                )
            },
        )
    }
}
