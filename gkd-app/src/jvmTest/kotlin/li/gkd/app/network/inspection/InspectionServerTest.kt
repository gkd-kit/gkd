package li.gkd.app.network.inspection

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage
import li.gkd.app.error.displayMessage
import li.gkd.app.error.resolveMessage
import li.gkd.app.model.ActionResult
import li.gkd.app.model.AppInfo
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.DeviceInfo
import li.gkd.app.model.GkdAction
import li.gkd.app.inspection.InspectionException
import li.gkd.app.inspection.InspectionFailure
import li.gkd.app.resources.*
import li.gkd.app.snapshot.SnapshotCaptureBusyException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.storage.AppStorageLayout
import li.gkd.app.storage.BackupIssue
import li.gkd.app.storage.FileTooLargeException
import li.gkd.app.storage.StorageException
import li.gkd.app.subscription.SubscriptionException
import li.gkd.app.subscription.SubscriptionFailureReason
import li.gkd.app.subscription.SubscriptionParseException
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.db.Db
import li.gkd.db.SubscriptionIds
import java.net.URI
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.getString

/** This exception is unknown to the server; only the shared business-error contract exposes it. */
private class ProtocolBusinessException : Exception("diagnostic-only"), BusinessError {
    override val errorCode = "TEST_BUSINESS_ERROR"
    override val errorPath = "$.input"
    override val errorMessage = ErrorMessage.WithDetail(
        ErrorMessage.Resource(Res.string.selector_type_error, listOf(ErrorMessage.Text("50% <&>\n输入"))),
        ErrorMessage.Resource(Res.string.snapshot_not_found),
    )
}

/** Runs inside the storage integration test's isolated database session. */
suspend fun assertInspectionProtocol(layout: AppStorageLayout, repository: SubscriptionRepository) {
    val snapshots = SnapshotStore
    val device = DeviceInfo("test", "model", "manufacturer", "brand", 33, "13")
    val app = AppInfo("test.app", "Test", 1, "1", false, 0, false, 0)
    val snapshot = ComplexSnapshot(7, app.id, null, 800, 400, false, app, app, device, emptyList())
    val json = Json { encodeDefaults = true; explicitNulls = true }
    // Archived Android payloads must remain readable independently of a host Application.
    val archived =
        json.decodeFromString<ComplexSnapshot>("""{"id":9,"appId":"legacy.app","activityId":null,"screenHeight":800,"screenWidth":400,"isLandscape":false,"appInfo":null,"gkdAppInfo":null,"device":{"device":"legacy","model":"phone","manufacturer":"vendor","brand":"brand","sdkInt":26,"release":"8"},"nodes":[]}""")
    assertEquals("legacy.app", archived.toSnapshot().appId)
    assertEquals(26, archived.device.sdkInt)
    assertNull(archived.appInfo)
    var received: GkdAction? = null
    val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
        configureInspectionServer(
            { ServerInfo(device, app) },
            { snapshot },
            {
                received = it
                when (it.selector) {
                    "fail" -> throw InspectionException(InspectionFailure.InvalidSelector)
                    "busy" -> throw SnapshotCaptureBusyException()
                    "unsupported" -> throw InspectionException(InspectionFailure.PlatformUnsupported)
                    "generic-business" -> throw ProtocolBusinessException()
                    "storage-failure" -> throw StorageException(BackupIssue.VersionUnsupported, "50% <&>")
                    "file-too-large" -> throw FileTooLargeException()
                    "crash" -> throw IllegalStateException("internal-test-failure", IOException("test cause"))
                        .apply { addSuppressed(IllegalArgumentException("test suppressed")) }
                    "stored-invalid" -> throw SerializationException("stored data is corrupt")
                    "stored-subscription-invalid" -> throw SubscriptionException(
                        SubscriptionFailureReason.SubscriptionFileParseFailed,
                        listOf("broken.json"),
                        SubscriptionParseException("$.id", "Expected an integer"),
                    )
                    "cancel" -> throw CancellationException("request cancelled")
                }
                ActionResult("clickNode", it.selector != "no-action")
            },
            "https://example.invalid/inspect.js",
            json
        )
        routing {
            post("/api/bytes-send-failure") {
                call.respond(object : OutgoingContent.ByteArrayContent() {
                    override val contentType = ContentType.Application.Json
                    override fun bytes(): ByteArray = throw IOException("bytes-send-failure")
                })
            }
            post("/api/channel-send-failure") {
                call.respond(object : OutgoingContent.ReadChannelContent() {
                    override val contentType = ContentType("image", "webp")
                    override fun readFrom(): ByteReadChannel = throw IOException("channel-send-failure")
                })
            }
        }
    }
    try {
        server.start()
        val port = server.engine.resolvedConnectors().single().port
        val client = HttpClient.newHttpClient()
        // Preflight returns the shared JSON success without executing the business route.
        val preflight = client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/execSelector"))
                .header("Origin", "https://example.invalid")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, preflight.statusCode())
        assertEquals("""{"message":"success"}""", preflight.body())
        assertTrue(preflight.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
        assertEquals(listOf("ok"), preflight.headers().allValues("GKD-RPC-Status"))
        for (header in listOf("Access-Control-Allow-Origin", "Access-Control-Allow-Methods",
            "Access-Control-Allow-Headers", "Access-Control-Expose-Headers")) {
            assertEquals("*", preflight.headers().firstValue(header).orElse(null))
        }
        assertEquals("true", preflight.headers().firstValue("Access-Control-Allow-Private-Network").orElse(null))
        assertNull(received)
        fun response(
            path: String,
            body: String = "{}",
            rpcStatus: String = "ok",
            contentType: String = "application/json",
        ): HttpResponse<String> {
            val response = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/$path"))
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, response.statusCode())
            // Header names are case-insensitive; the values are the RPC protocol discriminator.
            assertEquals(listOf(rpcStatus), response.headers().allValues("gkd-rpc-status"))
            assertEquals(
                "*",
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(null)
            )
            assertEquals("*", response.headers().firstValue("Access-Control-Expose-Headers").orElse(null))
            return response
        }

        fun request(path: String, body: String = "{}") =
            json.parseToJsonElement(response(path, body).body()).jsonObject
        fun error(path: String, body: String, code: String): JsonObject {
            val data = json.parseToJsonElement(response(path, body, "error").body()).jsonObject
            assertEquals(code, data.getValue("code").jsonPrimitive.content)
            assertTrue(data.getValue("message").jsonPrimitive.content.isNotBlank())
            assertFalse("__error" in data)
            assertFalse("unknown" in data)
            if (code != "INTERNAL_ERROR") {
                assertTrue(data["stackTrace"] == null || data["stackTrace"] == JsonNull)
            }
            return data
        }
        // Body acquisition can fail after the success hook but before the headers are committed.
        // The replacement error response must contain exactly one RPC status, without a stale "ok".
        error("bytes-send-failure", "{}", "INTERNAL_ERROR")
        error("channel-send-failure", "{}", "INTERNAL_ERROR")
        // These field names and nulls are the existing inspection HTTP / snapshot archive contract.
        val captured = request("captureSnapshot")
        assertEquals(JsonNull, captured["activityId"])
        assertEquals(7, captured.getValue("id").jsonPrimitive.int)
        assertEquals(snapshot, json.decodeFromJsonElement<ComplexSnapshot>(captured))
        snapshots.save(snapshot.id, write = { files ->
            files.snapshotFile.writeText(json.encodeToString(snapshot))
            files.minSnapshotFile.writeText(json.encodeToString(snapshot.copy(nodes = emptyList())))
            files.webpFile.writeText("image-bytes")
        }, publish = { Db.snapshotDao.insert(snapshot.toSnapshot()) })
        assertEquals(captured, request("getSnapshot", """{"id":7}"""))
        assertEquals("image-bytes", response("getScreenshot", """{"id":7}""").body())
        assertEquals(1, json.parseToJsonElement(response("getSnapshots").body()).jsonArray.size)
        // One broken file must not hide healthy snapshots or prevent future retries.
        val broken = snapshot.copy(id = 8)
        snapshots.save(8, write = { files ->
            files.snapshotFile.writeText("broken-json")
            files.minSnapshotFile.writeText("broken-json")
            files.webpFile.writeText("image-bytes")
        }, publish = { Db.snapshotDao.insert(broken.toSnapshot()) })
        try {
            assertEquals(1, json.parseToJsonElement(response("getSnapshots").body()).jsonArray.size)
            snapshots.snapshotFile(8).writeText(json.encodeToString(broken))
            assertEquals(2, json.parseToJsonElement(response("getSnapshots").body()).jsonArray.size)
            snapshots.snapshotFile(8).delete()
            layout.snapshot.resolve("8/8.min.json").delete()
            assertEquals(1, json.parseToJsonElement(response("getSnapshots").body()).jsonArray.size)
        } finally {
            snapshots.delete(broken.toSnapshot())
        }
        // Void RPC results share a fixed success message for debugging.
        val deleted = response("deleteSnapshot", """{"id":7}""")
        assertEquals("""{"message":"success"}""", deleted.body())
        assertTrue(deleted.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
        assertFalse(snapshots.snapshotFile(7).exists())
        error("getSnapshot", """{"id":7}""", "SNAPSHOT_NOT_FOUND")
        error("getScreenshot", """{"id":7}""", "SCREENSHOT_NOT_FOUND")
        error("deleteSnapshot", """{"id":7}""", "SNAPSHOT_NOT_FOUND")
        val action = request("execSelector", """{"selector":"[text=\"50% <&>\"]"}""")
        assertEquals(true, action.getValue("result").jsonPrimitive.boolean)
        assertEquals("[text=\"50% <&>\"]", received?.selector)
        error("execSelector", """{"selector":"fail"}""", "INVALID_SELECTOR")
        error("execSelector", """{"selector":"busy"}""", "SNAPSHOT_CAPTURE_BUSY")
        error("execSelector", """{"selector":"unsupported"}""", "PLATFORM_UNSUPPORTED")
        // A newly introduced business exception needs no concrete-type branch in the RPC plugin.
        // Nested resources, literal parameters and the optional path survive the real HTTP response.
        val business = error("execSelector", """{"selector":"generic-business"}""", "TEST_BUSINESS_ERROR")
        val expectedMessage = getString(Res.string.selector_type_error, "50% <&>\n输入") + "\n" +
            getString(Res.string.snapshot_not_found)
        assertEquals(expectedMessage, business.getValue("message").jsonPrimitive.content)
        assertEquals("$.input", business.getValue("path").jsonPrimitive.content)
        assertEquals(expectedMessage, ProtocolBusinessException().resolveMessage())
        assertEquals(expectedMessage, ProtocolBusinessException().displayMessage())
        val storage = error("execSelector", """{"selector":"storage-failure"}""", "VersionUnsupported")
        assertEquals(getString(Res.string.backup_version_unsupported, "50% <&>"), storage.getValue("message").jsonPrimitive.content)
        assertEquals(JsonNull, storage.getValue("path"))
        val oversized = error("execSelector", """{"selector":"file-too-large"}""", "FileTooLarge")
        assertEquals(getString(Res.string.file_too_large), oversized.getValue("message").jsonPrimitive.content)
        assertFalse(request("execSelector", """{"selector":"no-action"}""")
            .getValue("result").jsonPrimitive.boolean)
        // Incoming JSON failures are client errors; corrupt stored JSON remains an internal error.
        val lastReceived = received
        for (body in listOf("broken-json", "{}", """{"selector":{}}""")) {
            error("execSelector", body, "INVALID_REQUEST")
            assertEquals(lastReceived, received)
        }
        val mediaError = response("execSelector", "{}", "error", "text/plain")
        assertEquals("INVALID_REQUEST", json.parseToJsonElement(mediaError.body()).jsonObject
            .getValue("code").jsonPrimitive.content)
        error("getSnapshot", """{"id":"wrong-type"}""", "INVALID_REQUEST")
        error("execSelector", """{"selector":"stored-invalid"}""", "INTERNAL_ERROR")
        val storedError = error("execSelector", """{"selector":"stored-subscription-invalid"}""", "SubscriptionFileParseFailed")
        assertEquals(JsonNull, storedError.getValue("path"))
        assertEquals(
            getString(Res.string.subscription_file_parse_failed) + "\n$.id: Expected an integer",
            storedError.getValue("message").jsonPrimitive.content,
        )
        // Diagnostic stacks survive JSON transport, including the original cause and suppressed errors.
        val internal = json.parseToJsonElement(
            response("execSelector", """{"selector":"crash"}""", "error").body()
        ).jsonObject
        assertEquals("INTERNAL_ERROR", internal.getValue("code").jsonPrimitive.content)
        assertFalse(internal.getValue("message").jsonPrimitive.content.contains("internal-test-failure"))
        val serverStack = internal.getValue("stackTrace").jsonPrimitive.content
        assertTrue(serverStack.contains("java.lang.IllegalStateException: internal-test-failure"))
        assertTrue(serverStack.contains("Caused by: java.io.IOException: test cause"))
        assertTrue(serverStack.contains("Suppressed: java.lang.IllegalArgumentException: test suppressed"))
        assertTrue(serverStack.contains("at li.gkd.app.network.inspection.InspectionServerTestKt"))
        // Escaped cancellation is handled by CIO; it must never become a 200 RPC error.
        val cancelled = client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/execSelector"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"selector":"cancel"}""")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(500, cancelled.statusCode())
        assertFalse(cancelled.body().contains("INTERNAL_ERROR"))
        assertNull(cancelled.headers().firstValue("GKD-RPC-Status").orElse(null))
        // Unregistered routes remain HTTP-level failures, outside the RPC result contract.
        val missingRoute = client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/missing-route"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(404, missingRoute.statusCode())
        assertNull(missingRoute.headers().firstValue("GKD-RPC-Status").orElse(null))
        val updated = response("updateSubscription", """{"id":123,"name":"input","version":9,"apps":[]}""")
        assertEquals("""{"message":"success"}""", updated.body())
        assertTrue(updated.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
        val saved = repository.awaitSnapshot().subscriptions.getValue(SubscriptionIds.LOCAL_HTTP)
        assertEquals(0, saved.version)
        assertEquals("@gkd-kit/inspect", saved.author)
        assertTrue(layout.subscription.resolve("${SubscriptionIds.LOCAL_HTTP}.json").isFile)
        // Invalid subscription input must not replace the previous committed subscription.
        val subscriptionFile = layout.subscription.resolve("${SubscriptionIds.LOCAL_HTTP}.json")
        val previousText = subscriptionFile.readText()
        for ((body, path) in listOf(
            "broken-json" to "$",
            "{}" to "$.id",
            """{"id":"bad","name":"input","version":1}""" to "$.id",
            """{"id":1,"name":"input","version":1,"apps":[{"id":"app","groups":[{"key":0,"name":"G","rules":[{"actionMaximum":"bad"}]}]}]}"""
                to "$.apps[0].groups[0].rules[0].actionMaximum",
        )) {
            val failure = error("updateSubscription", body, "INVALID_SUBSCRIPTION")
            assertEquals(path, failure.getValue("path").jsonPrimitive.content)
            assertTrue(failure.getValue("message").jsonPrimitive.content.contains(": "))
            assertEquals(previousText, subscriptionFile.readText())
            assertEquals(saved, repository.awaitSnapshot().subscriptions.getValue(SubscriptionIds.LOCAL_HTTP))
        }
        assertEquals(
            app.id,
            request("getServerInfo").getValue("gkdAppInfo").jsonObject.getValue("id").jsonPrimitive.content
        )
        assertSubscriptionRoutes(layout, repository) { method, body, status ->
            response(method, body, status).body()
        }
    } finally {
        server.stop(0, 1000)
    }
}
