package li.gkd.app.network

import com.sun.net.httpserver.HttpServer
import io.ktor.client.plugins.ResponseException
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real transport and decoding through the production client, inside the isolated app lifecycle. */
suspend fun assertUpdateClient(directory: File) {
    directory.mkdirs()
    val bytes = ByteArray(262_144) { (it % 251).toByte() }
    val finishStreaming = CountDownLatch(1)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/version.json") { request ->
        val body =
            """{"versionCode":2,"versionName":"2","downloadUrl":"gkd.apk","fileSize":${bytes.size}}""".toByteArray()
        request.responseHeaders.add("Content-Type", "application/json")
        request.sendResponseHeaders(200, body.size.toLong())
        request.responseBody.use { it.write(body) }
    }
    server.createContext("/gkd.apk") { request ->
        request.sendResponseHeaders(200, bytes.size.toLong())
        request.responseBody.use { it.write(bytes) }
    }
    for (status in listOf(403, 404, 503)) {
        server.createContext("/error-$status") { request ->
            val body = "download failed".toByteArray()
            request.sendResponseHeaders(status, body.size.toLong())
            request.responseBody.use { it.write(body) }
        }
    }
    server.createContext("/stream.apk") { request ->
        request.sendResponseHeaders(200, bytes.size.toLong())
        request.responseBody.use { output ->
            output.write(bytes, 0, bytes.size / 2)
            output.flush()
            check(finishStreaming.await(10, TimeUnit.SECONDS))
            output.write(bytes, bytes.size / 2, bytes.size / 2)
        }
    }
    server.start()
    try {
        val url = "http://127.0.0.1:${server.address.port}/version.json"
        // HTTP error bodies must fail before creating an installable-looking APK.
        for (status in listOf(403, 404, 503)) {
            val failedFile = directory.resolve("error-$status.apk")
            val error = assertFailsWith<ResponseException> {
                UpdateClient.download(URI(url).resolve("/error-$status").toString(), failedFile, 0) {}
            }
            assertEquals(status, error.response.status.value)
            assertFalse(failedFile.exists())
        }
        val version = UpdateClient.fetch(url)
        assertEquals(NewVersion(2, "2", "gkd.apk", bytes.size.toLong()), version)
        val file = directory.resolve("download.apk")
        var progress = 0f
        UpdateClient.download(
            URI(url).resolve(version.downloadUrl).toString(),
            file,
            version.fileSize
        ) { progress = it }
        assertTrue(bytes.contentEquals(file.readBytes()))
        assertEquals(1f, progress)
        // The first half must reach disk while the server is still withholding the second half.
        coroutineScope {
            val streamedFile = directory.resolve("stream.apk")
            val download = async(Dispatchers.IO) {
                UpdateClient.download(URI(url).resolve("/stream.apk").toString(), streamedFile, bytes.size.toLong()) {}
            }
            try {
                withTimeout(5_000) {
                    while (streamedFile.length() == 0L) delay(10)
                }
                assertFalse(download.isCompleted)
            } finally {
                finishStreaming.countDown()
            }
            download.await()
            assertTrue(bytes.contentEquals(streamedFile.readBytes()))
        }
    } finally {
        finishStreaming.countDown()
        server.stop(0)
        directory.deleteRecursively()
    }
}
