package li.songe.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

data class BuildAssetVariant(
    val name: String,
    val flavor: String,
    val buildType: String,
    val mappingFile: Provider<RegularFile>,
    val versionCode: Provider<Int>,
    val versionName: Provider<String>,
)

class BuildAssetAdapter<AndroidComponentsT, VariantT : Any>(
    val onVariants: (
        androidComponents: AndroidComponentsT,
        buildType: String?,
        action: Action<VariantT>,
    ) -> Unit,
    val addGeneratedSourceDirectory: (
        variant: VariantT,
        task: TaskProvider<GenerateSourcePathsTask>,
    ) -> Unit,
    val describe: (variant: VariantT) -> BuildAssetVariant,
    val computeTaskName: (
        variant: VariantT,
        action: String,
        subject: String,
    ) -> String,
)

fun <AndroidComponentsT, VariantT : Any> Project.configureBuildAssets(
    androidComponents: AndroidComponentsT,
    adapter: BuildAssetAdapter<AndroidComponentsT, VariantT>,
) {
    val generateSourcePaths = registerSourcePathsTask(this.gitInfo.commitId)
    adapter.onVariants(
        androidComponents,
        null,
        object : Action<VariantT> {
            override fun execute(variant: VariantT) {
                adapter.addGeneratedSourceDirectory(variant, generateSourcePaths)
            }
        },
    )

    val githubCookie = buildProperty("GITHUB_COOKIE")
        .orElse(buildProperty("GKD_GITHUB_COOKIE"))
    val apiAuthToken = buildProperty("GKD_API_AUTH_TOKEN")
    if (!githubCookie.isPresent || !apiAuthToken.isPresent) return

    adapter.onVariants(
        androidComponents,
        "release",
        object : Action<VariantT> {
            override fun execute(variant: VariantT) {
                registerBuildAssetUpload(
                    variant = adapter.describe(variant),
                    uploadTaskName = adapter.computeTaskName(
                        variant,
                        "upload",
                        "buildAsset",
                    ),
                    generateSourcePaths = generateSourcePaths,
                    githubCookie = githubCookie,
                    apiAuthToken = apiAuthToken,
                )
            }
        },
    )
}

private fun Project.registerBuildAssetUpload(
    variant: BuildAssetVariant,
    uploadTaskName: String,
    generateSourcePaths: TaskProvider<GenerateSourcePathsTask>,
    githubCookie: Provider<String>,
    apiAuthToken: Provider<String>,
) {
    val buildGitInfo = this.gitInfo
    val variantBuildKey = releaseBuildKey(variant.flavor, buildGitInfo.commitId)
    val cleanBuildKey = "${variant.flavor}-${buildGitInfo.commitId.take(16)}"
    val uploadBuildAsset = tasks.register(
        uploadTaskName,
        UploadBuildAssetTask::class.java,
    ) {
        description = "Creates and uploads the build archive"
        mappingFile.set(variant.mappingFile)
        sourcePathsFile.set(
            generateSourcePaths.flatMap {
                it.outputDirectory.file("source-paths.txt")
            },
        )
        buildKey.set(variantBuildKey)
        variantName.set(variant.name)
        flavor.set(variant.flavor)
        buildType.set(variant.buildType)
        commitId.set(buildGitInfo.commitId)
        commitTime.set(buildGitInfo.commitTime)
        tagName.set(buildGitInfo.tagName.orEmpty())
        includeGitMetadata.set(
            variantBuildKey.map { it == cleanBuildKey },
        )
        versionCode.set(variant.versionCode)
        versionName.set(variant.versionName)
        this.githubCookie.set(githubCookie)
        this.apiAuthToken.set(apiAuthToken)
    }
    val capitalizedVariantName = variant.name.replaceFirstChar(Char::uppercaseChar)
    val lifecycleTaskNames = setOf(
        "assemble$capitalizedVariantName",
        "bundle$capitalizedVariantName",
    )
    tasks.configureEach {
        if (name in lifecycleTaskNames) dependsOn(uploadBuildAsset)
    }
}
