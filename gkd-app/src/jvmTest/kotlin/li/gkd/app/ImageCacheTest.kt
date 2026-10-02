package li.gkd.app

import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import li.gkd.app.ui.image.ImageLoaders
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImageCacheTest {
    @Test
    fun networkImageSurvivesLoaderRestartInConfiguredCache() = runBlocking {
        val directory = Files.createTempDirectory("gkd-coil-cache").toFile()
        val calls = AtomicInteger()
        val buffer = ByteArrayOutputStream()
        ImageIO.write(
            BufferedImage(
                2,
                2,
                BufferedImage.TYPE_INT_ARGB
            ), "png", buffer
        )
        val bytes = buffer.toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/image.png") { exchange ->
            calls.incrementAndGet()
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.responseHeaders.add("Cache-Control", "max-age=3600")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val request = ImageRequest.Builder(PlatformContext.INSTANCE)
                .data("http://127.0.0.1:${server.address.port}/image.png").build()
            val first = ImageLoaders.create(PlatformContext.INSTANCE, directory)
            try {
                assertIs<SuccessResult>(first.execute(request))
            } finally {
                first.shutdown()
            }
            val second = ImageLoaders.create(PlatformContext.INSTANCE, directory)
            try {
                val result = assertIs<SuccessResult>(second.execute(request))
                assertEquals(DataSource.DISK, result.dataSource)
                assertEquals(1, calls.get())
                assertTrue(directory.listFiles().orEmpty().isNotEmpty())
            } finally {
                second.shutdown()
            }
        } finally {
            server.stop(0); directory.deleteRecursively()
        }
    }
}
