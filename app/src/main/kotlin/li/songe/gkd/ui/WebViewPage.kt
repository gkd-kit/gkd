package li.songe.gkd.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.blankj.utilcode.util.LogUtils
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.data.Value
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.client
import li.songe.gkd.util.copyText
import li.songe.gkd.util.openUri
import li.songe.gkd.util.throttle


@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun WebViewPage(
    initUrl: String,
) {
    val mainVm = LocalMainViewModel.current
    val navController = LocalNavController.current
    val webViewState = rememberWebViewState(url = initUrl)
    val webViewClient = remember { GkdWebViewClient() }
    val webView = remember { Value<WebView?>(null) }
    LaunchedEffect(null) {
        WebView.getCurrentWebViewPackage()?.versionName?.run { splitToSequence('.').first() }
    }
    Scaffold(modifier = Modifier, topBar = {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TopAppBar(
                modifier = Modifier.fillMaxWidth(),
                navigationIcon = {
                    IconButton(onClick = throttle { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                title = {
                    val loadingState = webViewState.loadingState
                    if (loadingState is LoadingState.Loading) {
                        val lineHeightDp = LocalDensity.current.run {
                            LocalTextStyle.current.lineHeight.toDp()
                        }
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .size(lineHeightDp),
                        )
                    } else {
                        // webViewState.pageTitle 在调用 reload 后会变成 null
                        Text(
                            text = webViewState.pageTitle ?: webView.value?.title ?: "",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (chromeVersion > 0 && chromeVersion < MINI_CHROME_VERSION) {
                        IconButton(onClick = throttle {
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "兼容性提示",
                                text = "检测到您的系统内置浏览器版本($chromeVersion)过低, 可能无法正常浏览网页文档\n\n建议自行升级版本后重启 GKD 再查看文档, 或点击右上角后在外部浏览器打开查阅\n\n若能正常浏览文档请忽略此项提示"
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    var expanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { expanded = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
                    }
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (webViewState.loadingState !is LoadingState.Loading) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "刷新页面")
                                    },
                                    onClick = {
                                        expanded = false
                                        webView.value?.reload()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(text = "复制链接")
                                },
                                onClick = {
                                    expanded = false
                                    copyText(webView.value?.url ?: initUrl)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = "外部打开")
                                },
                                onClick = {
                                    expanded = false
                                    openUri(webView.value?.url ?: initUrl)
                                }
                            )
                        }
                    }
                }
            )
        }
    }) { contentPadding ->
        WebView(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            state = webViewState,
            client = webViewClient,
            onCreated = {
                webView.value = it
                it.settings.apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
            }
        )
    }
}

// 兼容性检测为最近 3 年, 2022-03-29
private const val MINI_CHROME_VERSION = 100
private val chromeVersion by lazy {
    WebView.getCurrentWebViewPackage()?.versionName?.run {
        splitToSequence('.').first().toIntOrNull()
    } ?: 0
}

private const val DOC_CONFIG_URL =
    "https://registry.npmmirror.com/@gkd-kit/docs/latest/files/_config.json"

private const val DEBUG_JS_TEXT = """
<script>
(function () {
    document.write(
        '<scr' +
            'ipt src="https://registry.npmmirror.com/eruda/latest/files"></scr' +
            'ipt>',
    );
    document.write('<scr' + 'ipt>eruda.init();</scr' + 'ipt>');
})();
</script>
"""


@Serializable
private data class DocConfig(
    val mirrorBaseUrl: String,
    val htmlUrlMap: Map<String, String>
)

private class GkdWebViewClient : AccompanistWebViewClient() {
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request != null && request.url.host != "gkd.li") {
            openUri(request.url.toString())
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        try {
            if (request != null && request.run { isForMainFrame && url.host == "gkd.li" && method == "GET" }) {
                LogUtils.d(request.method, request.url)
                runBlocking(Dispatchers.IO) {
                    val docConfig = client.get(DOC_CONFIG_URL).body<DocConfig>()
                    val path = request.url.path.let { if (it.isNullOrEmpty()) "/" else it }
                    val textUrl = docConfig.htmlUrlMap[path]?.let { docConfig.mirrorBaseUrl + it }
                    if (textUrl != null) {
                        val textContent = client.get(textUrl).body<String>().let {
                            if (META.debuggable) {
                                DEBUG_JS_TEXT + it
                            } else {
                                it
                            }
                        }
                        return@runBlocking WebResourceResponse(
                            "text/html",
                            "UTF-8",
                            textContent.byteInputStream()
                        )
                    }
                    return@runBlocking null
                }?.let { return it }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return super.shouldInterceptRequest(view, request)
    }
}
