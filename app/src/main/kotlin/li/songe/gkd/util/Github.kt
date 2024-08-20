package li.songe.gkd.util

import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessageBuilder
import kotlinx.serialization.Serializable
import li.songe.gkd.data.GithubPoliciesAsset
import java.io.File

private const val GITHUB_UPLOAD_URL = "https://github.com/gkd-kit/inspect/issues/1"
private fun HttpMessageBuilder.setCommonHeaders(cookie: String) {
    header("Cookie", cookie)
    header("Referer", GITHUB_UPLOAD_URL)
    header("Origin", "https://github.com")
    header(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0"
    )
}

@Suppress("PropertyName")
@Serializable
private data class UploadPoliciesAssetsResponse(
    val upload_url: String,
    val asset_upload_url: String,
    val asset_upload_authenticity_token: String,
    val asset: GithubPoliciesAsset,
    val form: Map<String, String>,
)

private val tokenRegex by lazy {
    Regex("<input\\s+type=\"hidden\"\\s+value=\"([0-9a-zA-Z\\-_]+)\"\\s+data-csrf=\"true\"\\s+class=\"js-data-upload-policy-url-csrf\"\\s+/>")
}
private val repositoryIdRegex by lazy {
    Regex("data-upload-repository-id=\"([0-9]+)\"")
}

private data class Authenticity(
    val repositoryId: String,
    val authenticityToken: String,
)

private suspend fun getAuthenticity(cookie: String): Authenticity {
    val text = client.get(GITHUB_UPLOAD_URL) {
        setCommonHeaders(cookie)
    }.bodyAsText()
    if (!text.contains("data-login")) {
        error("用户未登录, 请更换 cookie")
    }
    val repositoryId =
        repositoryIdRegex.find(text)?.groupValues?.get(1) ?: error("repositoryId not found")
    val authenticityToken =
        tokenRegex.find(text)?.groupValues?.get(1) ?: error("authenticityToken not found")
    return Authenticity(repositoryId, authenticityToken)
}

// https://github.com/lisonge/user-attachments
suspend fun uploadFileToGithub(
    cookie: String,
    file: File,
    listener: ((progress: Float) -> Unit)
): GithubPoliciesAsset {
    val authenticity = getAuthenticity(cookie)

    // prepare upload asset
    val policiesResp = client.post("https://github.com/upload/policies/assets") {
        setCommonHeaders(cookie)
        setBody(MultiPartFormDataContent(formData {
            append("authenticity_token", authenticity.authenticityToken)
            append("content_type", "application/x-zip-compressed")
            append("name", "file.zip")
            append("size", file.length().toString())
            append("repository_id", authenticity.repositoryId)
        }))
    }.body<UploadPoliciesAssetsResponse>()

    // upload to s3
    client.post(policiesResp.upload_url) {
        setCommonHeaders(cookie)
        setBody(MultiPartFormDataContent(formData {
            policiesResp.form.forEach { (key, value) ->
                append(key, value)
            }
            append("file", file.readBytes(), Headers.build {
                append(HttpHeaders.ContentType, "application/x-zip-compressed")
                append(HttpHeaders.ContentDisposition, "filename=\"file.zip\"")
            })
        }))
        onUpload { bytesSentTotal, contentLength ->
            listener(bytesSentTotal / contentLength.toFloat())
        }
    }

    // check assets
    client.put("https://github.com" + policiesResp.asset_upload_url) {
        setCommonHeaders(cookie)
        header("Accept", "application/json")
        setBody(MultiPartFormDataContent(formData {
            append("authenticity_token", policiesResp.asset_upload_authenticity_token)
        }))
    }

    return policiesResp.asset
}

