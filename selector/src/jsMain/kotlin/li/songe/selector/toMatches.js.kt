package li.songe.selector

import kotlin.js.RegExp

// https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/js/src/kotlin/text/regex.kt
actual fun String.toMatches(): (input: CharSequence) -> Boolean {
    if (length >= 4 && startsWith("(?")) {
        for (i in 2 until length) {
            when (get(i)) {
                in 'a'..'z' -> {}
                in 'A'..'Z' -> {}
                ')' -> {
                    val flags = subSequence(2, i).toMutableList()
                        .apply { add('g'); add('u') }
                        .joinToString("")
                    val nativePattern = RegExp(substring(i + 1), flags)
                    return { input ->
                        nativePattern.reset()
                        val match = nativePattern.exec(input.toString())
                        match != null && match.index == 0 && nativePattern.lastIndex == input.length
                    }
                }

                else -> break
            }
        }
    }
    val regex = Regex(this)
    return { input -> regex.matches(input) }
}