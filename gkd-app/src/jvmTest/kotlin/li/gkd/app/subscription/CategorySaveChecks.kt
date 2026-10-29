package li.gkd.app.subscription

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import li.gkd.db.SubscriptionIds

/** Uses the integration test's real application lifetime and isolated subscription storage. */
suspend fun assertCategorySaveResults() {
    val id = SubscriptionIds.LOCAL
    val original = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id)
    val before = SubscriptionRepository.files.readBytes(id)
    try {
        assertEquals(
            SubscriptionEditResult.Rejected(SubscriptionFailureReason.CategoryNameRequired),
            SubscriptionRepository.saveCategory(original, null, " ", ""),
        )
        assertContentEquals(before, SubscriptionRepository.files.readBytes(id))

        // Two saves based on the same displayed version must not both commit.
        val results = coroutineScope {
            val first = async {
                SubscriptionRepository.saveCategory(
                    original,
                    null,
                    "Category save A",
                    "description",
                )
            }
            val second = async {
                SubscriptionRepository.saveCategory(
                    original,
                    null,
                    "Category save B",
                    "description",
                )
            }
            listOf(first.await(), second.await())
        }
        assertEquals(1, results.count { it == SubscriptionEditResult.Success(true) })
        assertEquals(
            1,
            results.count {
                it ==
                    SubscriptionEditResult.Rejected(
                        SubscriptionFailureReason.SubscriptionPreviewConflict,
                    )
            },
        )
        val saved = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id)
        assertEquals(
            SubscriptionJson.json.encodeToString(saved),
            SubscriptionJson.json.encodeToString(SubscriptionRepository.files.load(id)),
        )
        val category =
            saved.categories.single {
                it.key !in original.categories.map { category -> category.key }
            }
        val savedBytes = SubscriptionRepository.files.readBytes(id)

        assertEquals(
            SubscriptionEditResult.Success(false),
            SubscriptionRepository.saveCategory(saved, category.key, category.name, "description"),
        )
        assertEquals(
            SubscriptionEditResult.Rejected(SubscriptionFailureReason.CategoryNameDuplicate),
            SubscriptionRepository.saveCategory(saved, null, category.name, ""),
        )
        assertContentEquals(savedBytes, SubscriptionRepository.files.readBytes(id))

        val missing = original.copy(id = 987654321L)
        assertIs<SubscriptionEditResult.Rejected>(
                SubscriptionRepository.saveCategory(missing, null, "name", ""),
            )
            .let {
                assertEquals(SubscriptionFailureReason.SubscriptionMissingId, it.reason)
            }
    } finally {
        SubscriptionRepository.update(id) { it.copy(categories = original.categories) }
    }
}
