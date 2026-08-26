package li.gkd.gradle

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.io.Serializable
import java.security.MessageDigest

private const val GIT_INFO_SERVICE_NAME = "gkdGitInfo"

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
) : Serializable {
    val versionNameSuffix get() = if (tagName == null) ("-" + commitId.take(7)) else null
}

abstract class GitInfoValueSource : ValueSource<GitInfo, GitInfoValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val repositoryDirectory: DirectoryProperty
    }

    override fun obtain(): GitInfo {
        return readGitInfo(parameters.repositoryDirectory.get().asFile.absolutePath)
    }
}

abstract class GitOutputValueSource : ValueSource<String, GitOutputValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val repositoryDirectory: DirectoryProperty
        val arguments: ListProperty<String>
    }

    override fun obtain(): String {
        return runGitCommandBytes(
            parameters.repositoryDirectory.get().asFile.absolutePath,
            parameters.arguments.get(),
        ).toString(Charsets.UTF_8)
    }
}

abstract class GitInfoService : BuildService<GitInfoService.Parameters> {
    interface Parameters : BuildServiceParameters {
        val gitInfo: Property<GitInfo>
    }

    val gitInfo: GitInfo by lazy {
        parameters.gitInfo.get()
    }
}

val Project.gitInfo: GitInfo
    get() = gradle.sharedServices.registerIfAbsent(
        GIT_INFO_SERVICE_NAME,
        GitInfoService::class.java,
    ) {
        parameters.gitInfo.set(
            providers.of(GitInfoValueSource::class.java) {
                parameters.repositoryDirectory.set(rootProject.layout.projectDirectory)
            },
        )
    }.get().gitInfo

fun Project.releaseBuildKey(
    flavor: String,
    commitId: String,
): Provider<String> =
    providers.of(GitBuildKeyValueSource::class.java) {
        parameters.repositoryDirectory.set(rootProject.layout.projectDirectory)
        parameters.flavor.set(flavor)
        parameters.commitId.set(commitId)
    }

abstract class GitBuildKeyValueSource :
    ValueSource<String, GitBuildKeyValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val repositoryDirectory: DirectoryProperty
        val flavor: Property<String>
        val commitId: Property<String>
    }

    override fun obtain(): String {
        val repositoryStateId = readRepositoryStateId(
            parameters.repositoryDirectory.get().asFile.absolutePath,
            parameters.commitId.get(),
        )
        return "${parameters.flavor.get()}-${repositoryStateId.take(16)}"
    }
}

private fun readGitInfo(repositoryDirectory: String): GitInfo {
    val commitId = runGitCommand(repositoryDirectory, listOf("rev-parse", "HEAD"))
    return GitInfo(
        commitId = commitId,
        commitTime = runGitCommand(repositoryDirectory, listOf("log", "-1", "--format=%ct")) + "000",
        tagName = runCatching {
            runGitCommand(repositoryDirectory, listOf("describe", "--tags", "--exact-match"))
        }.getOrNull(),
    )
}

private fun readRepositoryStateId(
    repositoryDirectory: String,
    commitId: String,
): String {
    val status = runGitCommandBytes(
        repositoryDirectory,
        listOf("status", "--porcelain=v1", "-z", "--untracked-files=all"),
    )
    if (status.isEmpty()) return commitId

    val changedPaths = sequenceOf(
        runGitCommandBytes(
            repositoryDirectory,
            listOf("diff", "--name-only", "-z", "HEAD"),
        ),
        runGitCommandBytes(
            repositoryDirectory,
            listOf("ls-files", "--others", "--exclude-standard", "-z"),
        ),
    )
        .flatMap { output ->
            output.toString(Charsets.UTF_8)
                .splitToSequence('\u0000')
                .filter(String::isNotEmpty)
        }
        .distinct()
        .sorted()
        .toList()

    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(commitId.toByteArray(Charsets.UTF_8))
    digest.update(0)
    digest.update(status)
    changedPaths.forEach { path ->
        val file = File(repositoryDirectory, path)
        digest.update(0)
        digest.update(path.toByteArray(Charsets.UTF_8))
        if (file.isFile) {
            digest.update(0)
            digest.update(file.length().toString().toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(file.lastModified().toString().toByteArray(Charsets.UTF_8))
        }
    }
    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
