package li.gkd.app.subscription

class CategoryPolicy {
    fun validateEdit(
        subscription: RawSubscription,
        categoryKey: Int?,
        name: String,
    ) {
        require(subscription.isLocal) {
            throw SubscriptionException(SubscriptionFailureReason.RemoteCategoryEditUnsupported)
        }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) {
            throw SubscriptionException(SubscriptionFailureReason.CategoryNameRequired)
        }
        require(subscription.categories.none { it.key != categoryKey && it.name == trimmedName }) {
            throw SubscriptionException(SubscriptionFailureReason.CategoryNameDuplicate)
        }
        if (categoryKey == null) {
            require((subscription.categories.maxOfOrNull { it.key } ?: -1) < Int.MAX_VALUE) {
                throw SubscriptionException(SubscriptionFailureReason.CategoryKeyExhausted)
            }
        } else {
            check(subscription.categories.any { it.key == categoryKey }) {
                throw SubscriptionException(SubscriptionFailureReason.CategoryMissing)
            }
        }
    }

    fun previewEdit(
        subscription: RawSubscription,
        categoryKey: Int?,
        name: String,
        description: String,
    ): RawSubscription {
        validateEdit(subscription, categoryKey, name)
        val trimmedName = name.trim()
        val category = if (categoryKey == null) {
            val maxKey = subscription.categories.maxOfOrNull { it.key } ?: -1
            RawSubscription.RawCategory(maxKey + 1, trimmedName, null, null)
        } else {
            subscription.categories.find { it.key == categoryKey } ?: throw SubscriptionException(SubscriptionFailureReason.CategoryMissing)
        }
        return subscription.edit {
            putCategory(
                category.copy(
                    name = trimmedName,
                    desc = description.trim().ifEmpty { null })
            )
        }
    }
}
