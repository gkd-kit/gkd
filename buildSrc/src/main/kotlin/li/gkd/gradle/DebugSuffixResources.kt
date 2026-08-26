package li.gkd.gradle

import org.gradle.api.Project
import javax.xml.parsers.DocumentBuilderFactory

fun Project.readDebugSuffixResources(): List<Pair<String, String>> {
    val stringsFile = layout.projectDirectory.file("src/main/res/values/strings.xml").asFile
    val stringNodes = DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(stringsFile)
        .documentElement
        .getElementsByTagName("string")

    return buildList {
        for (index in 0 until stringNodes.length) {
            val node = stringNodes.item(index)
            if (node.attributes.getNamedItem("debug_suffix") != null) {
                val name = node.attributes.getNamedItem("name").nodeValue
                add(name to "${node.textContent}-debug")
            }
        }
    }
}
