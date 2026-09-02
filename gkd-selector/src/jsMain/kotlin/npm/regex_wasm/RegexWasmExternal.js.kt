@file:JsModule("regex-wasm")

package npm.regex_wasm

import kotlin.js.JsModule

public external fun toMatches(pattern: String): (String) -> Boolean
