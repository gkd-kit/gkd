package li.songe.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

@CacheableTask
abstract class GenerateSourcePathsTask : DefaultTask() {
    @get:Input
    abstract val sourcePaths: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val paths = sourcePaths.get()
        require(paths.isNotEmpty()) { "No tracked Kotlin source files found" }
        require(paths == paths.sorted()) { "Kotlin source paths must be sorted" }
        require(paths.size == paths.distinct().size) { "Duplicate Kotlin source paths found" }
        require(paths.none { '\r' in it || '\n' in it || '\\' in it }) {
            "Kotlin source paths must be single-line repository-relative paths"
        }
        outputDirectory.file("source-paths.txt").get().asFile.writeText(
            paths.joinToString(separator = "\n", postfix = "\n"),
            Charsets.UTF_8,
        )
    }
}

fun Project.registerSourcePathsTask(commitId: String): TaskProvider<GenerateSourcePathsTask> {
    val trackedKotlinSourcePaths = providers.of(GitOutputValueSource::class.java) {
        parameters.repositoryDirectory.set(rootProject.layout.projectDirectory)
        parameters.arguments.set(
            listOf(
                "ls-tree",
                "-r",
                "-z",
                "--name-only",
                commitId,
            ),
        )
    }.map { output ->
        output
            .split('\u0000')
            .filter { it.endsWith(".kt") }
            .filterNot { path ->
                "/jvmTest/kotlin/" in path ||
                    "/androidTest/kotlin/" in path ||
                    "/test/kotlin/" in path ||
                    "/li/songe/gradle/" in path
            }
            .sorted()
    }
    val generatedAssetsDirectory = layout.buildDirectory.dir("generated/assets/sourcePaths")
    return tasks.register("generateSourcePaths", GenerateSourcePathsTask::class.java) {
        sourcePaths.set(trackedKotlinSourcePaths)
        outputDirectory.set(generatedAssetsDirectory)
    }
}
