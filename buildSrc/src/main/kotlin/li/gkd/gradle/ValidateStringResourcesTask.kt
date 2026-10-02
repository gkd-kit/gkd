package li.gkd.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** Checks placeholder syntax without generating or restricting platform resource qualifiers. */
abstract class ValidateStringResourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirectory: DirectoryProperty

    @get:OutputFile
    abstract val validationMarker: RegularFileProperty

    init {
        validationMarker.convention(project.layout.buildDirectory.file("validation/string-resources.ok"))
    }

    @TaskAction
    fun validate() {
        val source = resourceDirectory.get().asFile
        source.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values") }
            .forEach { directory ->
                directory.listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
                    val document = DocumentBuilderFactory.newInstance().apply {
                        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    }.newDocumentBuilder().parse(file)
                    val root = document.documentElement
                    for (index in 0 until root.childNodes.length) {
                        val entry = root.childNodes.item(index) as? Element ?: continue
                        require(
                            entry.tagName in setOf(
                                "string",
                                "plurals",
                                "string-array"
                            )
                        ) { "Unsupported resource: ${entry.tagName}" }
                        val leaves = if (entry.tagName == "string") listOf(entry) else {
                            val items = entry.getElementsByTagName("item")
                            (0 until items.length).map { items.item(it) as Element }
                        }
                        leaves.forEach { leaf ->
                            val value = leaf.textContent
                            require(!Regex("%(?![1-9][0-9]*\\\$s)").containsMatchIn(value)) {
                                "${file.name}:${entry.getAttribute("name")}: only numbered string placeholders are allowed; format values in Kotlin"
                            }
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
