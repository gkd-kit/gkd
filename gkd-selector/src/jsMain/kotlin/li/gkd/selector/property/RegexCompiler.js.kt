package li.gkd.selector.property

import kotlin.js.asDynamic

internal actual fun String.compilePlatformRegex(): RegexCompileResult {
    return compileWasmRegex { pattern -> npm.regex_wasm.toMatches(pattern) }
}

internal fun String.compileWasmRegex(
    factory: (String) -> (String) -> Boolean,
): RegexCompileResult {
    val matches = try {
        factory(this)
    } catch (error: Throwable) {
        val name = error.asDynamic().name as? String
        val detail = listOfNotNull(name, error.message)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(": ")
            .ifEmpty { error.toString() }
        return RegexCompileResult.Failure(detail)
    }
    return RegexCompileResult.Success { input -> matches(input.toString()) }
}
