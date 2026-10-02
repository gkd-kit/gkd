package li.gkd.app.network

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real transport and decoding through the production client, inside the isolated app lifecycle. */
suspend fun assertUpdateClient(directory: File) {
    directory.mkdirs()
    val bytes = ByteArray(32_768) { (it % 251).toByte() }
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
    server.start()
    try {
        val url = "http://127.0.0.1:${server.address.port}/version.json"
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
    } finally {
        server.stop(0)
        directory.deleteRecursively()
    }
}
