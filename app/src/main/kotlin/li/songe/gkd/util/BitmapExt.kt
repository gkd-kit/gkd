package li.songe.gkd.util

import android.graphics.Bitmap

fun Bitmap.isFullTransparent(): Boolean {
    val bufferHeight = height.coerceAtMost(32)
    val pixels = IntArray(width * bufferHeight)
    var y = 0
    while (y < height) {
        val rows = (height - y).coerceAtMost(bufferHeight)
        getPixels(pixels, 0, width, 0, y, width, rows)
        repeat(width * rows) { index ->
            if (pixels[index] ushr 24 != 0) return false
        }
        y += rows
    }
    return true
}
