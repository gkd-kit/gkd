package li.songe.gradle

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val BUILD_ASSET_GET_URL =
    "https://api.gkd.li/build-asset/getBuildAsset"
private const val BUILD_ASSET_CREATE_URL =
    "https://api.gkd.li/build-asset/createBuildAsset"
private const val GITHUB_REPOSITORY_ID = "661952005"
private const val GITHUB_UPLOAD_FILE_NAME = "file.zip"
private const val GITHUB_UPLOAD_CONTENT_TYPE = "application/x-zip-compressed"
private const val GITHUB_REFERER = "https://github.com/gkd-kit/inspect/issues/46"
private const val GITHUB_ORIGIN = "https://github.com"
private const val GITHUB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0"
private const val GITHUB_POLICIES_URL = "https://github.com/upload/policies/assets"
private const val GITHUB_GRAPHQL_URL = "https://github.com/_graphql"
private const val GITHUB_ISSUE_ID = "I_kwDOJ3SWBc6viUWN"
private const val GITHUB_ISSUE_CONNECTION =
    "client:I_kwDOJ3SWBc6viUWN:__Issue__backTimelineItems_connection(visibleEventsOnly:true)"
private const val GITHUB_ISSUE_FRONT_CONNECTION =
    "client:I_kwDOJ3SWBc6viUWN:__Issue__frontTimelineItems_connection(visibleEventsOnly:true)"
private const val BUILD_ARCHIVE_METADATA_FILE_NAME = "build.json"
private const val MAPPING_FILE_NAME = "mapping.txt"
private const val SOURCE_PATHS_FILE_NAME = "source-paths.txt"

private data class UploadPolicy(
    val uploadUrl: String,
    val assetUploadUrl: String,
    val assetUploadAuthenticityToken: String,
    val assetId: Int,
    val assetHref: String,
    val form: Map<String, String>,
)

private class BuildAssetApiClient(
    private val authToken: String,
) : Closeable {
    private val client = HttpClient(OkHttp) {
        followRedirects = true
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    suspend fun getAssetId(buildKey: String): Int? {
        val response = client.get(BUILD_ASSET_GET_URL) {
            parameter("buildKey", buildKey)
        }
        val responseBody = response.requireSuccess("Querying build asset")
        val result = JsonSlurper().parseText(responseBody) ?: return null
        val responseObject = result as? Map<*, *>
            ?: error("Build asset query did not return a JSON object or null")
        responseObject.requireApiSuccess("Build asset query")
        return responseObject.requiredPositiveInt("assetId")
    }

    suspend fun create(buildKey: String, assetId: Int) {
        val response = client.post(BUILD_ASSET_CREATE_URL) {
            header(HttpHeaders.Authorization, "Bearer $authToken")
            contentType(ContentType.Application.Json)
            setBody(
                JsonOutput.toJson(
                    linkedMapOf(
                        "buildKey" to buildKey,
                        "assetId" to assetId,
                    ),
                ),
            )
        }
        val result = parseObject(
            response.requireSuccess("Creating build asset"),
            "Build asset creation",
        )
        result.requireApiSuccess("Build asset creation")
        require(result.requiredString("buildKey") == buildKey) {
            "Build asset creation returned a different buildKey"
        }
        require(result.requiredPositiveInt("assetId") == assetId) {
            "Build asset creation returned a different assetId"
        }
    }

    override fun close() {
        client.close()
    }
}

private class GithubAssetUploader(
    private val cookie: String,
) : Closeable {
    private val client = HttpClient(OkHttp) {
        followRedirects = true
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 600_000
            socketTimeoutMillis = 600_000
        }
    }

    suspend fun upload(file: File): Int {
        val policy = requestUploadPolicy(file)
        uploadToStorage(policy, file)
        finalizeUpload(policy)
        attachAndRemoveComment(policy.assetHref)
        return policy.assetId
    }

    override fun close() {
        client.close()
    }

    private suspend fun requestUploadPolicy(file: File): UploadPolicy {
        val response = client.post(GITHUB_POLICIES_URL) {
            githubHeaders()
            header("GitHub-Verified-Fetch", "true")
            header("X-Requested-With", "XMLHttpRequest")
            setBody(
                multipartBody(
                    linkedMapOf(
                        "repository_id" to GITHUB_REPOSITORY_ID,
                        "name" to GITHUB_UPLOAD_FILE_NAME,
                        "size" to file.length().toString(),
                        "content_type" to GITHUB_UPLOAD_CONTENT_TYPE,
                    ),
                ),
            )
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            error("GitHub cookie is invalid or expired")
        }
        val result = parseObject(
            response.requireSuccess("Requesting GitHub upload policy"),
            "GitHub upload policy",
        )
        val asset = result.requiredObject("asset")
        val assetId = (asset["id"] as? Number)?.toInt()
            ?: error("GitHub upload policy is missing asset.id")
        require(assetId > 0) { "GitHub asset id must be positive: $assetId" }
        val form = result.requiredObject("form").entries.associate { (key, value) ->
            key.toString() to value.toString()
        }
        return UploadPolicy(
            uploadUrl = result.requiredString("upload_url"),
            assetUploadUrl = result.requiredString("asset_upload_url"),
            assetUploadAuthenticityToken =
                result.requiredString("asset_upload_authenticity_token"),
            assetId = assetId,
            assetHref = asset.requiredString("href"),
            form = form,
        )
    }

    private suspend fun uploadToStorage(policy: UploadPolicy, file: File) {
        val response = client.post(policy.uploadUrl) {
            // uploadUrl points to external storage, so GitHub credentials must not be sent.
            setBody(multipartBody(fields = policy.form, file = file))
        }
        response.requireSuccess("Uploading build archive to GitHub storage")
    }

    private suspend fun finalizeUpload(policy: UploadPolicy) {
        val response = client.put(GITHUB_ORIGIN + policy.assetUploadUrl) {
            githubHeaders()
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setBody(
                multipartBody(
                    mapOf(
                        "authenticity_token" to policy.assetUploadAuthenticityToken,
                    ),
                ),
            )
        }
        response.requireSuccess("Finalizing GitHub build archive upload")
    }

    private suspend fun attachAndRemoveComment(assetHref: String) {
        val commentResult = graphql(
            persistedQueryName = "addCommentMutation",
            queryHash = "edafa18ab5734f05c9893cbc92d0dfb1",
            variables = mapOf(
                "connections" to listOf(GITHUB_ISSUE_CONNECTION),
                "input" to mapOf(
                    "body" to assetHref,
                    "subjectId" to GITHUB_ISSUE_ID,
                ),
            ),
        )
        val commentId = commentResult.requiredObject("data")
            .requiredObject("addComment")
            .requiredObject("timelineEdge")
            .requiredObject("node")
            .requiredString("id")

        delay(1_000)

        graphql(
            persistedQueryName = "updateIssueSubscriptionMutation",
            queryHash = "d0752b2e49295017f67c84f21bfe41a3",
            variables = mapOf(
                "input" to mapOf(
                    "state" to "UNSUBSCRIBED",
                    "subscribableId" to GITHUB_ISSUE_ID,
                ),
            ),
        )
        graphql(
            persistedQueryName = "deleteIssueCommentMutation",
            queryHash = "b0f125991160e607a64d9407db9c01b3",
            variables = mapOf(
                "connections" to listOf(
                    GITHUB_ISSUE_FRONT_CONNECTION,
                    GITHUB_ISSUE_CONNECTION,
                ),
                "input" to mapOf("id" to commentId),
            ),
        )
    }

    private suspend fun graphql(
        persistedQueryName: String,
        queryHash: String,
        variables: Map<String, Any>,
    ): Map<*, *> {
        val requestBody = JsonOutput.toJson(
            mapOf(
                "persistedQueryName" to persistedQueryName,
                "query" to queryHash,
                "variables" to variables,
            ),
        )
        val response = client.post(GITHUB_GRAPHQL_URL) {
            githubHeaders()
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header("GitHub-Verified-Fetch", "true")
            contentType(ContentType.Text.Plain.withCharset(Charsets.UTF_8))
            setBody(requestBody)
        }
        val result = parseObject(
            response.requireSuccess("Calling GitHub GraphQL $persistedQueryName"),
            "GitHub GraphQL $persistedQueryName",
        )
        val errors = result["errors"] as? Collection<*>
        require(errors.isNullOrEmpty()) {
            "GitHub GraphQL $persistedQueryName returned errors"
        }
        return result
    }

    private fun HttpRequestBuilder.githubHeaders() {
        header(HttpHeaders.Cookie, cookie)
        header("Referer", GITHUB_REFERER)
        header("Origin", GITHUB_ORIGIN)
        header(HttpHeaders.UserAgent, GITHUB_USER_AGENT)
    }
}

@DisableCachingByDefault(because = "Uploads a build archive to external services")
abstract class UploadBuildAssetTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappingFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourcePathsFile: RegularFileProperty

    @get:Input
    abstract val buildKey: Property<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val flavor: Property<String>

    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val commitId: Property<String>

    @get:Input
    abstract val commitTime: Property<String>

    @get:Input
    abstract val tagName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val versionName: Property<String>

    @get:Internal
    abstract val githubCookie: Property<String>

    @get:Internal
    abstract val apiAuthToken: Property<String>

    @TaskAction
    fun upload() {
        val cookie = githubCookie.get().trim()
        val authToken = apiAuthToken.get().trim()
        val resolvedBuildKey = buildKey.get()
        require(cookie.isNotEmpty()) { "GitHub cookie must not be blank" }
        require(authToken.isNotEmpty()) { "GKD_API_AUTH_TOKEN must not be blank" }

        val existingAssetId = runBlocking {
            BuildAssetApiClient(authToken).use { apiClient ->
                apiClient.getAssetId(resolvedBuildKey)
            }
        }
        if (existingAssetId != null) {
            logger.lifecycle(
                "Build asset already exists: $resolvedBuildKey -> $existingAssetId",
            )
            return
        }

        val mapping = mappingFile.get().asFile
        require(mapping.isFile && mapping.length() > 0L) {
            "mapping.txt is missing or empty: ${mapping.absolutePath}"
        }
        val sourcePaths = sourcePathsFile.get().asFile
        require(sourcePaths.isFile && sourcePaths.length() > 0L) {
            "source-paths.txt is missing or empty: ${sourcePaths.absolutePath}"
        }

        val zipFile = temporaryDir.resolve(GITHUB_UPLOAD_FILE_NAME)
        createBuildArchive(mapping, sourcePaths, zipFile)
        logger.lifecycle(
            "Uploading build archive for $resolvedBuildKey as $GITHUB_UPLOAD_FILE_NAME " +
                "(${zipFile.length()} bytes)",
        )
        val assetId = runBlocking {
            val uploadedAssetId = GithubAssetUploader(cookie).use { uploader ->
                uploader.upload(zipFile)
            }
            BuildAssetApiClient(authToken).use { apiClient ->
                apiClient.create(resolvedBuildKey, uploadedAssetId)
            }
            uploadedAssetId
        }
        logger.lifecycle("Build asset created: $resolvedBuildKey -> $assetId")
    }

    private fun createBuildArchive(
        mappingFile: File,
        sourcePathsFile: File,
        zipFile: File,
    ) {
        val metadata = linkedMapOf(
            "schemaVersion" to 1,
            "buildKey" to buildKey.get(),
            "variant" to variantName.get(),
            "flavor" to flavor.get(),
            "buildType" to buildType.get(),
            "versionCode" to versionCode.get(),
            "versionName" to versionName.get(),
            "git" to linkedMapOf(
                "commitId" to commitId.get(),
                "commitTime" to commitTime.get().toLong(),
                "tagName" to tagName.get().takeIf(String::isNotEmpty),
            ),
            "files" to listOf(
                fileMetadata(MAPPING_FILE_NAME, mappingFile),
                fileMetadata(SOURCE_PATHS_FILE_NAME, sourcePathsFile),
            ),
        )
        val metadataText = JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n"

        zipFile.parentFile.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { output ->
            output.writeEntry(
                BUILD_ARCHIVE_METADATA_FILE_NAME,
                metadataText.toByteArray(Charsets.UTF_8),
            )
            output.writeEntry(MAPPING_FILE_NAME, mappingFile)
            output.writeEntry(SOURCE_PATHS_FILE_NAME, sourcePathsFile)
        }
    }
}

private fun fileMetadata(path: String, file: File): Map<String, Any> {
    return linkedMapOf(
        "path" to path,
        "size" to file.length(),
        "sha256" to file.sha256(),
    )
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    BufferedInputStream(inputStream()).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val readSize = input.read(buffer)
            if (readSize < 0) break
            digest.update(buffer, 0, readSize)
        }
    }
    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

private fun ZipOutputStream.writeEntry(path: String, content: ByteArray) {
    putNextEntry(ZipEntry(path).apply { time = 0L })
    write(content)
    closeEntry()
}

private fun ZipOutputStream.writeEntry(path: String, file: File) {
    putNextEntry(ZipEntry(path).apply { time = 0L })
    BufferedInputStream(file.inputStream()).use { input ->
        input.copyTo(this)
    }
    closeEntry()
}

private fun multipartBody(
    fields: Map<String, String>,
    file: File? = null,
): MultiPartFormDataContent {
    return MultiPartFormDataContent(
        formData {
            fields.forEach { (name, value) ->
                append(name, value)
            }
            if (file != null) {
                appendInput(
                    key = "file",
                    headers = Headers.build {
                        append(
                            HttpHeaders.ContentDisposition,
                            "filename=\"$GITHUB_UPLOAD_FILE_NAME\"",
                        )
                        append(HttpHeaders.ContentType, GITHUB_UPLOAD_CONTENT_TYPE)
                    },
                    size = file.length(),
                ) {
                    file.inputStream().asInput()
                }
            }
        },
    )
}

private suspend fun HttpResponse.requireSuccess(operation: String): String {
    val responseBody = bodyAsText()
    require(status.value in 200..299) {
        "$operation failed with HTTP ${status.value}"
    }
    return responseBody
}

private fun parseObject(value: String, description: String): Map<*, *> {
    return JsonSlurper().parseText(value) as? Map<*, *>
        ?: error("$description did not return a JSON object")
}

private fun Map<*, *>.requiredObject(key: String): Map<*, *> {
    return this[key] as? Map<*, *>
        ?: error("Missing JSON object: $key")
}

private fun Map<*, *>.requiredString(key: String): String {
    return this[key] as? String
        ?: error("Missing JSON string: $key")
}

private fun Map<*, *>.requiredPositiveInt(key: String): Int {
    val number = this[key] as? Number
        ?: error("Missing JSON integer: $key")
    val value = number.toLong()
    require(
        number.toDouble() == value.toDouble() &&
            value in 1..Int.MAX_VALUE.toLong(),
    ) {
        "JSON integer must be a positive 32-bit value: $key"
    }
    return value.toInt()
}

private fun Map<*, *>.requireApiSuccess(operation: String) {
    if (this["error"] == true) {
        val message = this["message"] as? String ?: "Unknown API error"
        error("$operation failed: $message")
    }
}
