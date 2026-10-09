package li.gkd.app.subscription

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import li.gkd.app.SimulatorStore
import li.gkd.db.Db
import li.gkd.db.SubsItem

/** Real failed updates: dismiss only the displayed error, preserving data and newer failures. */
suspend fun assertUpdateErrorDismissal(simulator: SimulatorStore) {
    val id = 87654321L
    val otherId = id + 1
    val response = AtomicReference("invalid-json")
    val gate = AtomicReference<Pair<CompletableDeferred<Unit>, CountDownLatch>?>(null)
    val otherRequests = AtomicInteger()
    val movedRequests = AtomicInteger()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/subscription") { exchange ->
        gate.get()?.let { (entered, release) ->
            entered.complete(Unit)
            check(release.await(10, TimeUnit.SECONDS))
        }
        val bytes = response.get().toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }
    server.createContext("/other") { exchange ->
        otherRequests.incrementAndGet()
        val bytes = """{"id":$otherId,"name":"Other subscription","version":2}""".toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }
    server.createContext("/moved/") { exchange ->
        movedRequests.incrementAndGet()
        val text = if (exchange.requestURI.path.endsWith("version")) {
            """{"id":$id,"version":3}"""
        } else response.get()
        val bytes = text.toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }
    server.start()
    val previous = simulator.settings.value
    try {
        simulator.update { it.copy(device = it.device.copy(wifi = true)) }
        val original = RawSubscription(id = id, name = "Update error fixture", version = 1)
        SubscriptionRepository.saveWithItem(
            original,
            SubsItem(id = id, order = 100, updateUrl = "http://127.0.0.1:${server.address.port}/subscription"),
        )
        val originalItem = Db.subsItemDao.queryAll().single { it.id == id }
        SubscriptionRepository.refresh()
        val first = SubscriptionRepository.awaitSnapshot().updateErrors.getValue(id)
        SubscriptionRepository.dismissUpdateError(id, first)
        assertFalse(id in SubscriptionRepository.awaitSnapshot().updateErrors)
        assertEquals(original, SubscriptionRepository.awaitSnapshot().subscriptions[id])
        assertEquals(originalItem, Db.subsItemDao.queryAll().single { it.id == id })

        SubscriptionRepository.refresh()
        val second = SubscriptionRepository.awaitSnapshot().updateErrors.getValue(id)
        assertTrue(first !== second)
        SubscriptionRepository.dismissUpdateError(id, first)
        assertTrue(SubscriptionRepository.awaitSnapshot().updateErrors[id] === second)
        SubscriptionRepository.dismissUpdateError(id, second)
        assertFalse(id in SubscriptionRepository.awaitSnapshot().updateErrors)

        SubscriptionRepository.refresh()
        assertTrue(id in SubscriptionRepository.awaitSnapshot().updateErrors)
        response.set("""{"id":$id,"name":"Update error fixture","version":2}""")
        SubscriptionRepository.refresh()
        assertFalse(id in SubscriptionRepository.awaitSnapshot().updateErrors)
        assertEquals(2, SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id).version)

        SubscriptionRepository.saveWithItem(
            RawSubscription(otherId, "Other subscription", 1),
            SubsItem(id = otherId, order = 101, updateUrl = "http://127.0.0.1:${server.address.port}/other"),
        )
        // A targeted refresh must neither request nor update the other subscription.
        SubscriptionRepository.refresh(id)
        assertEquals(0, otherRequests.get())
        assertEquals(1, SubscriptionRepository.awaitSnapshot().subscriptions.getValue(otherId).version)

        // Hold a real request open: single and full refreshes reject each other in both directions.
        for (target in listOf(id, null)) {
            coroutineScope {
                val entered = CompletableDeferred<Unit>()
                val release = CountDownLatch(1)
                gate.set(entered to release)
                val active = async { SubscriptionRepository.refresh(target) }
                try {
                    withTimeout(10_000) { entered.await() }
                    assertEquals(SubscriptionResult.Busy, SubscriptionRepository.refresh())
                    assertEquals(SubscriptionResult.Busy, SubscriptionRepository.refresh(otherId))
                } finally {
                    gate.set(null)
                    release.countDown()
                }
                active.await()
            }
        }
        assertEquals(1, otherRequests.get())
        assertEquals(2, SubscriptionRepository.awaitSnapshot().subscriptions.getValue(otherId).version)

        // Editing a URL performs no request; later refresh uses it even if content names an old URL.
        val beforeEdit = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id).copy(
            updateUrl = "http://127.0.0.1:${server.address.port}/subscription",
            checkUpdateUrl = "version",
        )
        SubscriptionRepository.saveWithItem(beforeEdit, originalItem)
        val itemBeforeEdit = Db.subsItemDao.queryAll().single { it.id == id }
        val newUrl = "http://127.0.0.1:${server.address.port}/moved/subscription"
        response.set("invalid-json")
        assertEquals(
            SubscriptionResult.Success(SubscriptionResult.SuccessKind.Modified),
            SubscriptionRepository.addOrModifyRemote(newUrl, itemBeforeEdit.id),
        )
        assertEquals(0, movedRequests.get())
        assertEquals(itemBeforeEdit.copy(updateUrl = newUrl), Db.subsItemDao.queryAll().single { it.id == id })
        assertEquals(beforeEdit, SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id))
        SubscriptionRepository.refresh(id)
        assertEquals(2, movedRequests.get())
        assertTrue(id in SubscriptionRepository.awaitSnapshot().updateErrors)
        assertEquals(beforeEdit, SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id))
        assertEquals(newUrl, Db.subsItemDao.queryAll().single { it.id == id }.updateUrl)
        response.set("""{"id":$id,"name":"Update error fixture","version":3}""")
        SubscriptionRepository.refresh(id)
        assertEquals(4, movedRequests.get())
        assertFalse(id in SubscriptionRepository.awaitSnapshot().updateErrors)
        assertEquals(3, SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id).version)
    } finally {
        server.stop(0)
        SubscriptionRepository.delete(id, otherId)
        simulator.replace(previous)
    }
}
