package li.songe.gkd.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import li.songe.gkd.data.GithubPoliciesAsset
import java.io.File

private fun HttpMessageBuilder.setCommonHeaders(cookie: String) {
    header("Cookie", cookie)
    header("Referer", "https://github.com/")
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

data class GithubCookieException(override val message: String) : Exception(message)

// https://github.com/lisonge/user-attachments
suspend fun uploadFileToGithub(
    cookie: String,
    file: File,
    listener: ((progress: Float) -> Unit)
): GithubPoliciesAsset {
    // prepare upload asset
    val policiesRawResp = client.post("https://github.com/upload/policies/assets") {
        setCommonHeaders(cookie)
        header("GitHub-Verified-Fetch", "true")
        header("X-Requested-With", "XMLHttpRequest")
        setBody(MultiPartFormDataContent(formData {
            append("repository_id", "661952005")
            append("name", "file.zip")
            append("size", file.length().toString())
            append("content_type", "application/x-zip-compressed")
        }))
    }
    if (policiesRawResp.status == HttpStatusCode.Unauthorized) {
        throw GithubCookieException("检测到 cookie 失效, 请更换")
    }
    val policiesResp = policiesRawResp.body<UploadPoliciesAssetsResponse>()

    // upload to s3
    val byteArray = file.readBytes()
    client.post(policiesResp.upload_url) {
        setCommonHeaders(cookie)
        setBody(MultiPartFormDataContent(formData {
            policiesResp.form.forEach { (key, value) ->
                append(key, value)
            }
            append("file", byteArray, Headers.build {
                append(HttpHeaders.ContentType, "application/x-zip-compressed")
                append(HttpHeaders.ContentDisposition, "filename=\"file.zip\"")
            })
        }))
        onUpload { bytesSentTotal, contentLength ->
            listener(bytesSentTotal / (contentLength ?: byteArray.size).toFloat())
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

@Composable
fun EditGithubCookieDlg(showEditCookieDlgFlow: MutableStateFlow<Boolean>) {
    val showEditCookieDlg by showEditCookieDlgFlow.collectAsState()
    if (showEditCookieDlg) {
        val privacyStore by privacyStoreFlow.collectAsState()
        var value by remember {
            mutableStateOf(privacyStore.githubCookie ?: "")
        }
        val inputFocused = rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                if (!inputFocused.value) {
                    showEditCookieDlgFlow.value = false
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Github Cookie")
                    IconButton(onClick = throttle {
                        openUri(ShortUrlSet.URL1)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = null,
                        )
                    }
                }
            },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it.filter { c -> c != '\n' && c != '\r' }
                    },
                    placeholder = { Text(text = "请输入 Github Cookie") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) {
                                inputFocused.value = true
                            }
                        },
                    maxLines = 10,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditCookieDlgFlow.value = false
                    privacyStoreFlow.update { it.copy(githubCookie = value.trim()) }
                }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCookieDlgFlow.value = false }) {
                    Text(text = "取消")
                }
            }
        )
    }
}

