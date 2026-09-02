package li.gkd.gradle

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project

data class NpmDependency(
    val name: String,
    val version: String,
)

fun Project.readNpmDependencies(): List<NpmDependency> {
    val packageFile = layout.projectDirectory.file("package.json")
    val content = providers.fileContents(packageFile).asText.get()
    val packageJson = JsonSlurper().parseText(content) as? Map<*, *>
        ?: throw GradleException("package.json must contain a JSON object")
    val dependencies = packageJson["dependencies"] as? Map<*, *>
        ?: return emptyList()

    return dependencies.map { (name, version) ->
        NpmDependency(
            name = name as? String
                ?: throw GradleException("Invalid dependency name in package.json"),
            version = version as? String
                ?: throw GradleException("Invalid dependency version for $name"),
        )
    }
}
