package li.songe.json5

import kotlinx.serialization.json.*
import java.lang.StringBuilder
import kotlin.collections.set
import kotlin.let
import kotlin.ranges.contains
import kotlin.text.endsWith
import kotlin.text.getOrNull
import kotlin.text.substring
import kotlin.text.toDouble
import kotlin.text.toInt
import kotlin.text.toLong
import kotlin.text.trimEnd

// https://spec.json5.org/
internal class Json5Decoder(private val input: CharSequence) {
    private var i = 0
    private val char: Char?
        get() = input.getOrNull(i)
    private val end: Boolean
        get() = i >= input.length

    private fun stop(): Nothing {
        if (end) {
            error("Unexpected Char: EOF")
        }
        error("Unexpected Char: $char at index $i")
    }

    fun read(): JsonElement {
        val root = i == 0
        readUseless()
        val element = when (char) {
            '{' -> readObject()
            '[' -> readArray()
            '"', '\'' -> JsonPrimitive(readString())
            in '0'..'9', '-', '+', '.', 'N', 'I' -> JsonPrimitive(readNumber())
            't' -> { // true
                i++
                next('r')
                next('u')
                next('e')
                JsonPrimitive(true)
            }

            'f' -> { // false
                i++
                next('a')
                next('l')
                next('s')
                next('e')
                JsonPrimitive(false)
            }

            'n' -> { // null
                i++
                next('u')
                next('l')
                next('l')
                JsonNull
            }

            else -> stop()
        }
        if (root) {
            readUseless()
            if (!end) {
                stop()
            }
        }
        return element
    }

    private fun next(c: Char) {
        if (c == char) {
            i++
            return
        }
        stop()
    }

    private fun readObject(): JsonObject {
        i++
        readUseless()
        if (char == '}') {
            i++
            return JsonObject(emptyMap())
        }
        val map = mutableMapOf<String, JsonElement>()
        while (true) {
            readUseless()
            val key = readObjectKey()
            readUseless()
            next(':')
            readUseless()
            val value = read()
            map[key] = value
            readUseless()
            if (char == '}') {
                i++
                break
            } else if (char == ',') {
                i++
                readUseless()
                if (char == '}') {
                    i++
                    break
                }
            } else {
                stop()
            }
        }
        return JsonObject(map)
    }

    private fun readObjectKey(): String {
        val c = char
        if (c == '\'' || c == '"') {
            return readString()
        }
        val sb = StringBuilder()
        if (c == '\\') {
            i++
            next('u')
            repeat(4) {
                if (!isHexDigit(char)) {
                    stop()
                }
                i++
            }
            val n = input.substring(i - 4, i).toInt(16).toChar()
            if (!isIdStartChar(n)) {
                stop()
            }
            sb.append(n)
        } else if (!isIdStartChar(c)) {
            stop()
        } else {
            sb.append(c)
        }
        i++
        while (!end) {
            if (char == '\\') {
                i++
                next('u')
                repeat(4) {
                    if (!isHexDigit(char)) {
                        stop()
                    }
                    i++
                }
                val n = input.substring(i - 4, i).toInt(16).toChar()
                if (!isIdContinueChar(n)) {
                    stop()
                }
                sb.append(n)
            } else if (isIdContinueChar(char)) {
                sb.append(char)
                i++
            } else {
                break
            }
        }
        return sb.toString()
    }

    private fun readArray(): JsonArray {
        i++
        readUseless()
        if (char == ']') {
            i++
            return JsonArray(emptyList())
        }
        val list = mutableListOf<JsonElement>()
        while (true) {
            readUseless()
            list.add(read())
            readUseless()
            if (char == ']') {
                i++
                break
            } else if (char == ',') {
                i++
                readUseless()
                if (char == ']') {
                    i++
                    break
                }
            } else {
                stop()
            }
        }
        return JsonArray(list)
    }

    private fun readString(): String {
        val wrapChar = char!!
        i++
        val sb = StringBuilder()
        while (true) {
            when (char) {
                null -> stop()
                wrapChar -> {
                    i++
                    break
                }

                '\\' -> {
                    i++
                    when (char) {
                        null -> stop()
                        wrapChar -> {
                            sb.append(wrapChar)
                            i++
                        }

                        'x' -> {
                            i++
                            repeat(2) {
                                if (!isHexDigit(char)) {
                                    stop()
                                }
                                i++
                            }
                            val hex = input.substring(i - 2, i)
                            sb.append(hex.toInt(16).toChar())
                        }

                        'u' -> {
                            i++
                            repeat(4) {
                                if (!isHexDigit(char)) {
                                    stop()
                                }
                                i++
                            }
                            val hex = input.substring(i - 4, i)
                            sb.append(hex.toInt(16).toChar())
                        }

                        '\'' -> {
                            sb.append('\'')
                            i++
                        }

                        '\"' -> {
                            sb.append('\"')
                            i++
                        }

                        '\\' -> {
                            sb.append('\\')
                            i++
                        }

                        'b' -> {
                            sb.append('\b')
                            i++
                        }

                        'f' -> {
                            sb.append('\u000C')
                            i++
                        }

                        'n' -> {
                            sb.append('\n')
                            i++
                        }

                        'r' -> {
                            sb.append('\r')
                            i++
                        }

                        't' -> {
                            sb.append('\t')
                            i++
                        }

                        'v' -> {
                            sb.append('\u000B')
                            i++
                        }

                        '0' -> {
                            sb.append('\u0000')
                            i++
                            if (isDigit(char)) {
                                stop()
                            }
                        }

                        // multiline string
                        '\u000D' -> {// \r
                            i++
                            if (char == '\u000A') {// \n
                                i++
                            }
                        }

                        // multiline string
                        '\u000A', '\u2028', '\u2029' -> {
                            i++
                        }

                        in '1'..'9' -> stop()

                        else -> {
                            sb.append(char)
                        }
                    }
                }

                else -> {
                    sb.append(char)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun readNumber(signal: Boolean = false): Number {
        return when (char) {
            '-' -> {
                if (!signal) {
                    i++
                    val n = readNumber(true)
                    if (n is Double) {
                        return -n
                    }
                    if (n is Long) {
                        return -n
                    }
                    if (n is Int) {
                        return -n
                    }
                    stop()
                } else {
                    stop()
                }
            }

            '+' -> {
                if (!signal) {
                    i++
                    return readNumber(true)
                } else {
                    stop()
                }
            }

            'N' -> {// NaN
                i++
                next('a')
                next('N')
                Double.NaN
            }

            'I' -> {// Infinity
                i++
                next('n')
                next('f')
                next('i')
                next('n')
                next('i')
                next('t')
                next('y')
                Double.POSITIVE_INFINITY
            }

            '.' -> {
                var start = i
                i++
                readInteger()
                val numPart = input.substring(start, i).trimEnd('0').let {
                    if (it == ".") { // .0 -> 0
                        "0"
                    } else {
                        it
                    }
                }
                if (numPart == "0") {
                    0L
                } else {
                    if (isPowerStartChar(char)) {
                        start = i + 1
                        readNumberPower()
                        val power = input.substring(start, i)
                        (numPart + power).toDouble()
                    } else {
                        input.substring(start, i).toDouble()
                    }
                }
            }

            in '0'..'9' -> {
                var start = i
                var hasHex = false
                if (char == '0') { // 0x11
                    i++
                    if (isDigit(char)) {// not allow 00 01
                        stop()
                    } else if (isHexStartChar(char)) {
                        i++
                        hasHex = true
                    }
                }
                if (hasHex) {
                    if (!isHexDigit(char)) {
                        stop()
                    }
                    i++
                    while (!end && isHexDigit(char)) {
                        i++
                    }
                    input.substring(start + 2, i).toLong(16)
                } else {
                    var hasPoint = false // 1.2
                    while (!end) {
                        if (char == '.') {
                            if (!hasPoint) {
                                hasPoint = true
                            } else {
                                stop()
                            }
                        } else if (!isDigit(char)) {
                            break
                        }
                        i++
                    }
                    val hasEndPoint = hasPoint && input[i - 1] == '.' // kotlin not support 1.
                    val numPart = if (hasEndPoint) {
                        hasPoint = false
                        input.substring(start, i - 1) // 1. -> 1
                    } else {
                        if (hasPoint) {
                            input.substring(start, i).trimEnd('0').let { // 1.10 -> 1.1, 1.0 -> 1.
                                if (it.endsWith('.')) { // 1. -> 1
                                    hasPoint = false
                                    it.substring(0, it.length - 1)
                                } else {
                                    it
                                }
                            }
                        } else {
                            input.substring(start, i)
                        }
                    }
                    if (isPowerStartChar(char)) {
                        start = i
                        readNumberPower()
                        val power = input.substring(start, i)
                        (numPart + power).toDouble()
                    } else {
                        if (hasPoint) {
                            numPart.toDouble()
                        } else {
                            numPart.run { toLongOrNull() ?: toDouble() }
                        }
                    }
                }
            }

            else -> stop()
        }
    }

    private fun readInteger() {
        val start = i
        while (isDigit(char)) {
            i++
        }
        if (start == i) {
            stop()
        }
    }

    private fun readNumberPower() {
        i++
        if (char == '-' || char == '+') {
            i++
        }
        readInteger()
    }

    private fun readUseless() {
        while (true) {
            val oldIndex = i
            readCommentOrWhitespace()
            if (oldIndex == i) {
                return
            }
        }
    }

    private fun readCommentOrWhitespace() {
        when {
            char == '/' -> {
                i++
                when (char) {
                    '/' -> {
                        i++
                        while (!isNewLine(char) && !end) {
                            i++
                        }
                    }

                    '*' -> {
                        i++
                        while (true) {
                            when (char) {
                                null -> stop()
                                '*' -> {
                                    if (input.getOrNull(i + 1) == '/') {
                                        i += 2
                                        break
                                    }
                                }
                            }
                            i++
                        }
                    }

                    else -> stop()
                }
            }

            isWhiteSpace(char) -> {
                i++
                while (isWhiteSpace(char)) {
                    i++
                }
            }
        }
    }
}
