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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.ramcosta.composedestinations.generated.destinations.WebViewPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import li.songe.gkd.data.GithubPoliciesAsset
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.local.LocalMainViewModel
import li.songe.json5.Json5
import java.io.File

private fun HttpMessageBuilder.setCommonHeaders(cookie: String) {
    header("Cookie", cookie)
    header("Referer", "https://github.com/gkd-kit/inspect/issues/46")
    header("Origin", "https://github.com")
    header(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0"
    )
}

private fun String.json5ToJsonString(): String {
    return json.encodeToString(Json5.parseToJson5Element(this))
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

private suspend fun graphqlFetch(
    cookie: String,
    data: String,
): HttpResponse {
    return client.post("https://github.com/_graphql") {
        setCommonHeaders(cookie)
        header("Accept", "application/json")
        header("Content-Type", "text/plain;charset=UTF-8")
        header("GitHub-Verified-Fetch", "true")
        setBody(data)
    }
}

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

    // send file url text to github comment
    val commentResultResp = graphqlFetch(
        cookie,
        """
        {
            query: '50e7774b5a519b88858e02e46e0348da',
            variables: {
              connections: [
                'client:I_kwDOJ3SWBc6viUWN:__Issue__backTimelineItems_connection(visibleEventsOnly:true)',
              ],
              input: {
                body: '${policiesResp.asset.href}',
                subjectId: 'I_kwDOJ3SWBc6viUWN',
              },
            },
        }
        """.json5ToJsonString()
    )
    val commentResult = json.decodeFromString<JsonElement>(commentResultResp.bodyAsText())
    val commentId = (commentResult.jsonObject["data"]
        ?.jsonObject["addComment"]
        ?.jsonObject["timelineEdge"]
        ?.jsonObject["node"]
        ?.jsonObject["id"]?.toString() ?: error("commentId not found"))

    // delay is needed
    delay(1000)

    // unsubscribe the comment
    graphqlFetch(
        cookie,
        """
        {
            query: 'd0752b2e49295017f67c84f21bfe41a3',
            variables: {
                input: { state: 'UNSUBSCRIBED', subscribableId: 'I_kwDOJ3SWBc6viUWN' },
            },
        }
        """.json5ToJsonString()
    )

    // delete the comment
    graphqlFetch(
        cookie,
        """
        {
            query: 'b0f125991160e607a64d9407db9c01b3',
            variables: {
                connections: [],
                input: { id: $commentId },
            },
        }
        """.json5ToJsonString()
    )
    return policiesResp.asset
}

@Composable
fun EditGithubCookieDlg(showEditCookieDlgFlow: MutableStateFlow<Boolean>) {
    val showEditCookieDlg by showEditCookieDlgFlow.collectAsState()
    val mainVm = LocalMainViewModel.current
    if (showEditCookieDlg) {
        val privacyStore by privacyStoreFlow.collectAsState()
        var value by remember {
            mutableStateOf(privacyStore.githubCookie ?: "")
        }
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = {
                showEditCookieDlgFlow.value = false
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Github Cookie")
                    IconButton(onClick = throttle {
                        showEditCookieDlgFlow.value = false
                        mainVm.navController.toDestinationsNavigator()
                            .navigate(WebViewPageDestination(initUrl = ShortUrlSet.URL1))
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
                        .autoFocus(),
                    maxLines = 10,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditCookieDlgFlow.value = false
                    privacyStoreFlow.update { it.copy(githubCookie = value.trim()) }
                    toast("更新成功")
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

