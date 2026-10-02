package li.gkd.app.model

private fun Char.isAsciiLetter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z'
}

private fun Char.isAsciiVar(): Boolean {
    return this.isAsciiLetter() || this in '0'..'9' || this == '_'
}

private fun Char.isAsciiClassVar(): Boolean {
    return this.isAsciiVar() || this == '$'
}

// https://developer.android.com/build/configure-app-module?hl=zh-cn
fun String.isValidAppId(): Boolean {
    if (!contains('.')) return false
    if (!first().isAsciiLetter()) return false
    var i = 0
    while (i < length) {
        val c = get(i)
        if (c == '.') {
            i++
            if (getOrNull(i)?.isAsciiLetter() != true) {
                return false
            }
        } else if (!c.isAsciiVar()) {
            return false
        }
        i++
    }
    return true
}

fun String.isValidActivityId(): Boolean {
    if (isEmpty()) return false
    var i = 0
    while (i < length) {
        val c = get(i)
        if (c == '.') {
            i++
            if (getOrNull(i)?.isAsciiClassVar() == false) {
                return false
            }
        } else if (!c.isAsciiClassVar()) {
            return false
        }
        i++
    }
    return true
}

