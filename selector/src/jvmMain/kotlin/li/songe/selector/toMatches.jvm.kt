package li.songe.selector

actual fun String.toMatches(): (input: CharSequence) -> Boolean {
    val regex = Regex(this)
    return { input -> regex.matches(input) }
}