package li.songe.gradle

import org.gradle.api.Project

internal fun runGitCommandBytes(
    repositoryDirectory: String,
    arguments: List<String>,
): ByteArray {
    val process = ProcessBuilder(
        listOf("git", "-C", repositoryDirectory) + arguments,
    ).redirectErrorStream(true).start()
    val output = process.inputStream.readBytes()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        error("Command failed with exit code $exitCode: ${output.toString(Charsets.UTF_8)}")
    }
    return output
}

private fun runGitCommand(
    repositoryDirectory: String,
    arguments: List<String>,
): String {
    return runGitCommandBytes(repositoryDirectory, arguments)
        .toString(Charsets.UTF_8)
        .trim()
}

data class GitInfo(
    val commitId: String,
    val commitTime: String,
    val tagName: String?,
) {
    val versionNameSuffix get() = if (tagName == null) ("-" + commitId.take(7)) else null
}

fun Project.readGitInfo(): GitInfo {
    val repositoryDirectory = rootProject.layout.projectDirectory.asFile.absolutePath
    return GitInfo(
        commitId = runGitCommand(repositoryDirectory, listOf("rev-parse", "HEAD")),
        commitTime = runGitCommand(repositoryDirectory, listOf("log", "-1", "--format=%ct")) + "000",
        tagName = runCatching {
            runGitCommand(repositoryDirectory, listOf("describe", "--tags", "--exact-match"))
        }.getOrNull(),
    )
}
