package li.songe.gkd.ui.style

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import li.songe.json5.Json5
import li.songe.json5.Json5Token


val surfaceCardColors: CardColors
    @Composable
    get() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

private fun getDarkJson5TokenColor(json5Token: Json5Token?): Color = when (json5Token) {
    null -> Color(0xFFFF00FF) // unknown token color
    Json5Token.Comment -> Color(0xFF75715E)
    Json5Token.LeftBrace, Json5Token.RightBrace -> Color(0xFFFFA07A)
    Json5Token.LeftBracket, Json5Token.RightBracket -> Color(0xFFFFA07A)
    Json5Token.Colon -> Color(0xFFE1E4E8)
    Json5Token.Comma -> Color(0xFFE1E4E8)
    Json5Token.BooleanLiteral -> Color(0xFF79B8FF)
    Json5Token.NullLiteral -> Color(0xFFB22222)
    Json5Token.NumberLiteral -> Color(0xFF2E8B57)
    Json5Token.StringLiteral -> Color(0xFFE6DB74)
    Json5Token.Property -> Color(0xFFBCBEC4)
    Json5Token.Whitespace -> Color.Transparent
}

private fun getLightJson5TokenColor(json5Token: Json5Token?): Color = when (json5Token) {
    null -> Color(0xFFFF0000)
    Json5Token.Comment -> Color(0xFF6A9955)
    Json5Token.LeftBrace, Json5Token.RightBrace -> Color(0xFFAF00DB)
    Json5Token.LeftBracket, Json5Token.RightBracket -> Color(0xFFAF00DB)
    Json5Token.Colon -> Color(0xFF000000)
    Json5Token.Comma -> Color(0xFF000000)
    Json5Token.BooleanLiteral -> Color(0xFF0000FF)
    Json5Token.NullLiteral -> Color(0xFFA31515)
    Json5Token.NumberLiteral -> Color(0xFF098658)
    Json5Token.StringLiteral -> Color(0xFF669900)
    Json5Token.Property -> Color(0xFF001080)
    Json5Token.Whitespace -> Color.Transparent
}

private val json5LightStyleCache = HashMap<Json5Token?, SpanStyle>()
private val json5DarkStyleCache = HashMap<Json5Token?, SpanStyle>()

fun getJson5AnnotatedString(source: String, dark: Boolean): AnnotatedString = buildAnnotatedString {
    append(source)
    val styleCache = if (dark) {
        json5DarkStyleCache
    } else {
        json5LightStyleCache
    }
    Json5.parseToJson5LooseRanges(source).forEach { range ->
        if (range.token is Json5Token.Whitespace) {
            return@forEach
        }
        val style = styleCache[range.token] ?: SpanStyle(
            color = if (dark) {
                getDarkJson5TokenColor(range.token)
            } else {
                getLightJson5TokenColor(range.token)
            },
        ).apply {
            styleCache[range.token] = this
        }
        addStyle(
            style = style,
            range.start,
            range.end
        )
    }
}
