package li.gkd.app.network

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.model.ActionResult
import li.gkd.app.model.AppInfo
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.DeviceInfo
import li.gkd.app.model.GkdAction
import li.gkd.app.model.RpcError
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.storage.AppStorageLayout
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.db.Db
import li.gkd.db.LOCAL_HTTP_SUBS_ID
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                if (it.selector == "fail") throw RpcError("expected failure")
                ActionResult("clickNode", true)
            },
            "https://example.invalid/inspect.js",
            json
        )
    }
    try {
        server.start()
        val port = server.engine.resolvedConnectors().single().port
        val client = HttpClient.newHttpClient()
        fun response(path: String, body: String = "{}"): HttpResponse<String> {
            val response = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/$path"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, response.statusCode())
            assertEquals(
                "*",
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(null)
            )
            return response
        }

        fun request(path: String, body: String = "{}") =
            json.parseToJsonElement(response(path, body).body()).jsonObject
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
        request("deleteSnapshot", """{"id":7}""")
        assertFalse(snapshots.snapshotFile(7).exists())
        assertEquals(
            true,
            request("getSnapshot", """{"id":7}""").getValue("__error").jsonPrimitive.boolean
        )
        val action = request("execSelector", """{"selector":"[text=\"50% <&>\"]"}""")
        assertEquals(true, action.getValue("result").jsonPrimitive.boolean)
        assertEquals("[text=\"50% <&>\"]", received?.selector)
        assertEquals(
            true,
            request(
                "execSelector",
                """{"selector":"fail"}"""
            ).getValue("__error").jsonPrimitive.boolean
        )
        request("updateSubscription", """{"id":123,"name":"input","version":9,"apps":[]}""")
        val saved = repository.awaitSnapshot().subscriptions.getValue(LOCAL_HTTP_SUBS_ID)
        assertEquals(0, saved.version)
        assertEquals("@gkd-kit/inspect", saved.author)
        assertTrue(layout.subscription.resolve("$LOCAL_HTTP_SUBS_ID.json").isFile)
        assertEquals(
            app.id,
            request("getServerInfo").getValue("gkdAppInfo").jsonObject.getValue("id").jsonPrimitive.content
        )
    } finally {
        server.stop(0, 1000)
    }
}
