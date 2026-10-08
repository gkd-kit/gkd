package li.gkd.app.ui.image

import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import li.gkd.app.app
import li.gkd.app.util.AndroidStorage
import li.gkd.app.util.AndroidTarget

actual fun appImageLoader() = sharedImageLoader

private val sharedImageLoader by lazy {
    ImageLoaders.create(app, AndroidStorage.storage.coilCache) {
        if (AndroidTarget.P) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
    }
}
