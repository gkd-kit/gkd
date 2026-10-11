package li.gkd.app.backup

import java.util.zip.ZipFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNull
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionJson
import li.gkd.app.subscription.SubscriptionPersistence
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.db.SubsItem

/** Export during a committed file/DB change must wait for the matching published subscription. */
suspend fun assertBackupSnapshotConsistency() = coroutineScope {
    val original = RawSubscription(87654999, "Before export", 1)
    val updated = original.copy(name = "After export", version = 2)
    val item = SubsItem(id = original.id, order = 100)
    SubscriptionRepository.saveWithItem(original, item)
    try {
        val written = CompletableDeferred<Unit>()
        val publish = CompletableDeferred<Unit>()
        val mutation = async {
            SubscriptionRepository.withBackupTransaction(listOf(updated)) { subscriptions ->
                SubscriptionPersistence.save(subscriptions.single(), item.copy(order = 101))
                written.complete(Unit)
                publish.await()
            }
        }
        val export = async {
            written.await()
            BackupManager.exportData()
        }
        try {
            withTimeout(5_000) { written.await() }
            assertNull(withTimeoutOrNull(500) { export.await() })
        } finally {
            publish.complete(Unit)
        }
        mutation.await()
        val archive = withTimeout(5_000) { export.await() }
        try {
            ZipFile(archive).use { zip ->
                val configuration = zip.getInputStream(zip.getEntry("db.json")).bufferedReader().use {
                    BackupFormat.decode(it.readText())
                }
                val subscription = zip.getInputStream(zip.getEntry("subscription/${original.id}.json"))
                    .bufferedReader().use {
                        SubscriptionJson.json.decodeFromString<RawSubscription>(it.readText())
                    }
                assertEquals(101, configuration.subsItems.single { it.id == original.id }.order)
                assertEquals(
                    SubscriptionJson.json.encodeToString(updated),
                    SubscriptionJson.json.encodeToString(subscription),
                )
            }
        } finally {
            archive.delete()
        }
    } finally {
        SubscriptionRepository.delete(original.id)
    }
}
