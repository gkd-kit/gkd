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
import li.songe.json5.Json5SyntaxKind


val surfaceCardColors: CardColors
    @Composable
    get() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

private fun getDarkJson5SyntaxColor(kind: Json5SyntaxKind): Color = when (kind) {
    Json5SyntaxKind.Comment -> Color(0xFF75715E)
    Json5SyntaxKind.LeftBrace, Json5SyntaxKind.RightBrace -> Color(0xFFFFA07A)
    Json5SyntaxKind.LeftBracket, Json5SyntaxKind.RightBracket -> Color(0xFFFFA07A)
    Json5SyntaxKind.Colon -> Color(0xFFE1E4E8)
    Json5SyntaxKind.Comma -> Color(0xFFE1E4E8)
    Json5SyntaxKind.BooleanLiteral -> Color(0xFF79B8FF)
    Json5SyntaxKind.NullLiteral -> Color(0xFFB22222)
    Json5SyntaxKind.NumberLiteral -> Color(0xFF2E8B57)
    Json5SyntaxKind.StringLiteral -> Color(0xFFE6DB74)
    Json5SyntaxKind.PropertyName -> Color(0xFFBCBEC4)
    Json5SyntaxKind.Whitespace -> Color.Transparent
    Json5SyntaxKind.Identifier, Json5SyntaxKind.Invalid -> Color(0xFFFF00FF)
}

private fun getLightJson5SyntaxColor(kind: Json5SyntaxKind): Color = when (kind) {
    Json5SyntaxKind.Comment -> Color(0xFF6A9955)
    Json5SyntaxKind.LeftBrace, Json5SyntaxKind.RightBrace -> Color(0xFFAF00DB)
    Json5SyntaxKind.LeftBracket, Json5SyntaxKind.RightBracket -> Color(0xFFAF00DB)
    Json5SyntaxKind.Colon -> Color(0xFF000000)
    Json5SyntaxKind.Comma -> Color(0xFF000000)
    Json5SyntaxKind.BooleanLiteral -> Color(0xFF0000FF)
    Json5SyntaxKind.NullLiteral -> Color(0xFFA31515)
    Json5SyntaxKind.NumberLiteral -> Color(0xFF098658)
    Json5SyntaxKind.StringLiteral -> Color(0xFF669900)
    Json5SyntaxKind.PropertyName -> Color(0xFF001080)
    Json5SyntaxKind.Whitespace -> Color.Transparent
    Json5SyntaxKind.Identifier, Json5SyntaxKind.Invalid -> Color(0xFFFF0000)
}

private val json5LightStyleCache = HashMap<Json5SyntaxKind, SpanStyle>()
private val json5DarkStyleCache = HashMap<Json5SyntaxKind, SpanStyle>()

fun getJson5AnnotatedString(source: String, dark: Boolean): AnnotatedString = buildAnnotatedString {
    append(source)
    val styleCache = if (dark) {
        json5DarkStyleCache
    } else {
        json5LightStyleCache
    }
    Json5.scanSyntax(source) { kind, start, end ->
        val style = styleCache[kind] ?: SpanStyle(
            color = if (dark) {
                getDarkJson5SyntaxColor(kind)
            } else {
                getLightJson5SyntaxColor(kind)
            },
        ).apply {
            styleCache[kind] = this
        }
        addStyle(
            style = style,
            start,
            end,
        )
    }
}
