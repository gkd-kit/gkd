package li.gkd.app

import li.gkd.app.window.DesktopWindowGeometry
import org.jetbrains.skia.Image
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Reads the current Skia recording. Does not recompose a new scene or capture native HWNDs. */
object DesktopCapture {
    fun compose(window: Window, frame: Boolean): BufferedImage {
        val bounds = DesktopWindowGeometry.contentBounds(window, frame)
        val result = BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        try {
            fun draw(host: Window) {
                fun drawComponent(component: Component) {
                    if (!component.isVisible) return
                    if (component !is SkiaLayer) {
                        (component as? Container)?.components?.forEach(::drawComponent)
                        return
                    }
                    val layer = component
                    val bitmap =
                        requireNotNull(layer.screenshot()) { "Compose frame has not rendered yet" }
                    val picture = bitmap.use {
                        Image.makeFromBitmap(it).use { image ->
                            requireNotNull(image.encodeToData()).use { data ->
                                ImageIO.read(
                                    ByteArrayInputStream(data.bytes)
                                )
                            }
                        }
                    }
                    val origin = layer.canvas.locationOnScreen
                    graphics.drawImage(
                        picture, origin.x - bounds.x, origin.y - bounds.y,
                        layer.canvas.width, layer.canvas.height, null
                    )
                }
                drawComponent(host)
                host.ownedWindows.filter { it.isShowing }.forEach(::draw)
            }
            draw(window)
        } finally {
            graphics.dispose()
        }
        return result
    }
}
