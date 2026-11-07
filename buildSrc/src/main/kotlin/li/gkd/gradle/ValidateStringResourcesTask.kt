package li.gkd.gradle

import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/** Checks placeholder syntax without generating or restricting platform resource qualifiers. */
abstract class ValidateStringResourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirectory: DirectoryProperty

    @get:OutputFile abstract val validationMarker: RegularFileProperty

    init {
        validationMarker.convention(
            project.layout.buildDirectory.file("validation/string-resources.ok"),
        )
    }

    @TaskAction
    fun validate() {
        val source = resourceDirectory.get().asFile
        val entries = mutableMapOf<String, MutableMap<String, Map<String, Set<String>>>>()
        source
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .forEach { directory ->
                val directoryEntries = entries.getOrPut(directory.name) { mutableMapOf() }
                directory
                    .listFiles()
                    .orEmpty()
                    .filter { it.extension == "xml" }
                    .forEach { file ->
                        val document =
                            DocumentBuilderFactory.newInstance()
                                .apply {
                                    setFeature(
                                        "http://apache.org/xml/features/disallow-doctype-decl",
                                        true,
                                    )
                                }
                                .newDocumentBuilder()
                                .parse(file)
                        val root = document.documentElement
                        for (index in 0 until root.childNodes.length) {
                            val entry = root.childNodes.item(index) as? Element ?: continue
                            if (entry.tagName !in setOf("string", "plurals", "string-array"))
                                continue
                            val leaves =
                                if (entry.tagName == "string") listOf(entry)
                                else {
                                    val items = entry.getElementsByTagName("item")
                                    (0 until items.length).map { items.item(it) as Element }
                                }
                            if (entry.tagName == "plurals") {
                                require(leaves.any { it.getAttribute("quantity") == "other" }) {
                                    "${file.path}:${entry.getAttribute("name")}: missing other quantity"
                                }
                            }
                            leaves.forEach { leaf ->
                                val value = leaf.textContent
                                require(!Regex("%(?![1-9][0-9]*\\\$s)").containsMatchIn(value)) {
                                    "${file.name}:${entry.getAttribute("name")}: only numbered string placeholders are allowed; format values in Kotlin"
                                }
                            }
                            val key = "${entry.tagName}/${entry.getAttribute("name")}"
                            directoryEntries[key] =
                                leaves
                                    .mapIndexed { index, leaf ->
                                        val itemKey =
                                            if (entry.tagName == "plurals")
                                                leaf.getAttribute("quantity")
                                            else index.toString()
                                        itemKey to
                                            Regex("%[1-9][0-9]*\\\$s|\\\$\\{[^}]+}")
                                                .findAll(leaf.textContent)
                                                .map { it.value }
                                                .toSet()
                                    }
                                    .toMap()
                        }
                    }
            }
        val defaults = entries.getValue("values")
        entries
            .filterKeys { it != "values" }
            .forEach { (directory, localized) ->
                localized.forEach resource@{ (key, parameters) ->
                    val original = defaults[key] ?: return@resource
                    if (key.startsWith("plurals/")) {
                        require(parameters.values.all { it == original.getValue("other") }) {
                            "$directory:$key: translated plural parameters differ from default"
                        }
                    } else {
                        require(parameters == original) {
                            "$directory:$key: translated parameters differ from default"
                        }
                    }
                }
            }
        // Gradle fingerprints the inputs and task implementation; this only records success.
        validationMarker.get().asFile.apply {
            parentFile.mkdirs()
            writeText("validated\n")
        }
    }
}
