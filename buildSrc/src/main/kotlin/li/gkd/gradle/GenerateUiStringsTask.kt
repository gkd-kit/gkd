package li.gkd.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** Single-language accessors keep shared presentation text usable without an Android Context. */
@CacheableTask
abstract class GenerateUiStringsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stringsFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(stringsFile.get().asFile)
            .documentElement.getElementsByTagName("string")
        val source = buildString {
            appendLine("// Generated from res/values/strings.xml. Do not edit.")
            appendLine("package li.gkd.app.text")
            appendLine()
            appendLine("object UiStrings {")
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as Element
                // Platform labels can have build-variant suffixes; read those through R.string.
                if (node.hasAttribute("debug_suffix")) continue
                val name = node.getAttribute("name")
                require(name.matches(Regex("[a-z][a-z0-9_]*"))) { "Invalid string name: $name" }
                val value = decodeAndroidString(node.textContent)
                val parameters = if (node.getAttribute("formatted") == "false") emptyList() else
                    Regex("%(\\d+)\\\$s").findAll(value).map { it.groupValues[1].toInt() }.distinct().sorted().toList()
                val literal = quoteKotlin(value)
                if (parameters.isEmpty()) {
                    appendLine("    const val $name: String = $literal")
                } else {
                    require(parameters == (1..parameters.size).toList()) { "Non-contiguous arguments: $name" }
                    val args = parameters.joinToString { "arg$it: Any?" }
                    val values = parameters.joinToString { "arg$it" }
                    appendLine("    fun $name($args): String = String.format(java.util.Locale.ROOT, $literal, $values)")
                }
            }
            appendLine("}")
        }
        outputDirectory.file("li/gkd/app/text/UiStrings.kt").get().asFile.apply {
            parentFile.mkdirs()
            writeText(source, Charsets.UTF_8)
        }
    }
}

// Resources with significant literal whitespace use Android's quoted form.
private fun decodeAndroidString(value: String): String {
    val text = if (value.startsWith('"') && value.endsWith('"')) value.substring(1, value.length - 1) else value
    return buildString {
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            if (char != '\\' || index == text.length) {
                append(char)
            } else {
                when (val escaped = text[index++]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'u' -> {
                        append(text.substring(index, index + 4).toInt(16).toChar())
                        index += 4
                    }
                    else -> append(escaped)
                }
            }
        }
    }
}

private fun quoteKotlin(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        append(when (char) {
            '\\' -> "\\\\"
            '"' -> "\\\""
            '$' -> "\\$"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> char.toString()
        })
    }
    append('"')
}
