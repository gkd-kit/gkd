package li.gkd.app.network.inspection

import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import java.io.IOException
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.DesktopRuntime
import li.gkd.app.DesktopStorage
import li.gkd.app.ServicePhase
import li.gkd.app.SimulatedService
import li.gkd.app.settings.SettingsRepository

/** Real listener stop/restart and port publication inside the single application test lifetime. */
suspend fun assertDesktopDebugServiceLifecycle(runtime: DesktopRuntime) {
    val original = SettingsRepository.settings.value
    val connection = DesktopStorage.sessionDirectory.resolve("debug-server.json")
    val client = HttpClient.newHttpClient()
    fun port() =
        Json.parseToJsonElement(connection.readText()).jsonObject.getValue("port").jsonPrimitive.int
    fun get(port: Int, path: String) =
        client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    try {
        val first = ServerSocket(0).use { it.localPort }
        withContext(Dispatchers.Main.immediate) {
            SettingsRepository.updateSettings {
                it.copy(httpServerPort = first, autoClearMemorySubs = false)
            }
            runtime.debugService.bindControls { get("/probe") { call.respondText("window-bound") } }
        }
        assertEquals(first, port())
        assertEquals("window-bound", get(first, "desktop/probe").body())
        withContext(Dispatchers.Main.immediate) { runtime.debugService.setEnabled(false) }
        assertFalse(connection.exists())
        assertEquals(
            ServicePhase.Stopped,
            runtime.simulator.settings.value.services.state(SimulatedService.Debug).phase,
        )
        assertFailsWith<IOException> { get(first, "health") }

        // Re-enable through the same API used by the page and F12 controls, without rebinding
        // windows.
        withContext(Dispatchers.Main.immediate) { runtime.debugService.setEnabled(true) }
        assertEquals(first, port())
        assertEquals("window-bound", get(first, "desktop/probe").body())
        val second = ServerSocket(0).use { it.localPort }
        withContext(Dispatchers.Main.immediate) {
            SettingsRepository.updateSettings { it.copy(httpServerPort = second) }
            runtime.debugService.restartIfRunning()
        }
        assertEquals(second, port())
        assertEquals(200, get(second, "health").statusCode())
        assertEquals("window-bound", get(second, "desktop/probe").body())
        assertFailsWith<IOException> { get(first, "health") }
    } finally {
        withContext(Dispatchers.Main.immediate) {
            runtime.debugService.unbindControls()
            SettingsRepository.updateSettings {
                it.copy(
                    httpServerPort = original.httpServerPort,
                    autoClearMemorySubs = original.autoClearMemorySubs,
                )
            }
        }
    }
    assertFalse(connection.exists())
}
