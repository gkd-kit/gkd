package li.gkd.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

@CacheableTask
abstract class CollectConsumerProguardRulesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimeJars: ConfigurableFileCollection

    @get:OutputFile
    abstract val rulesFile: RegularFileProperty

    @TaskAction
    fun collect() {
        val rulePath = Regex("META-INF/proguard/[^/]+\\.pro")
        // Append every rule file with its origin, so identical filenames never overwrite each other.
        val rules = buildString {
            appendLine("# Generated from JVM runtime dependencies; do not edit.")
            runtimeJars.files.sortedBy { it.name }.forEach { jar ->
                ZipFile(jar).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && rulePath.matches(it.name) }
                        .sortedBy { it.name }
                        .forEach { entry ->
                            appendLine()
                            appendLine("# ${jar.name}!/${entry.name}")
                            val content = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8)
                                .use { it.readText() }
                            appendLine(content.removePrefix("\uFEFF").replace("\r\n", "\n"))
                        }
                }
            }
        }
        rulesFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(rules, Charsets.UTF_8)
        }
    }
}
