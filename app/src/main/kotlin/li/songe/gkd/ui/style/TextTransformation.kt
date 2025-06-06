package li.songe.gkd.ui.style

import androidx.collection.LruCache
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private const val HIGHLIGHT_JSON5_MAX_LENGTH = 10000

private class Json5VisualTransformation(val dark: Boolean) : VisualTransformation {
    val cache = LruCache<String, TransformedText>(0xF)
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.isBlank() || text.text.length > HIGHLIGHT_JSON5_MAX_LENGTH) {
            return VisualTransformation.None.filter(text)
        }
        cache[text.text]?.let { return it }
        return TransformedText(
            text = getJson5AnnotatedString(text.text, dark),
            offsetMapping = OffsetMapping.Identity,
        ).apply {
            cache.put(text.text, this)
        }
    }
}

private val darkVisualTransformation = Json5VisualTransformation(true)
private val lightVisualTransformation = Json5VisualTransformation(false)

fun getJson5Transformation(dark: Boolean): VisualTransformation = if (dark) {
    darkVisualTransformation
} else {
    lightVisualTransformation
}

fun clearJson5TransformationCache() {
    darkVisualTransformation.cache.evictAll()
    lightVisualTransformation.cache.evictAll()
}
