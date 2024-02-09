package li.songe.gkd.util

import android.graphics.Bitmap

fun Bitmap.isEmptyBitmap(): Boolean {
    val emptyBitmap = Bitmap.createBitmap(width, height, config)
    return this.sameAs(emptyBitmap)
}