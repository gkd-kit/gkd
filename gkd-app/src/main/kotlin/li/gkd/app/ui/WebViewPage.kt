package li.gkd.app.ui

import li.gkd.app.MainViewModel

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavKey
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.LogUtils
import li.gkd.app.util.client
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar

@Serializable
data class WebViewRoute(val initUrl: String) : NavKey

@Composable
fun WebViewPage(route: WebViewRoute) {
    val initUrl = route.initUrl
    val mainVm = MainViewModel.requireCurrent()
    val webViewState = rememberWebViewState(url = initUrl)
    val webViewClient = remember { GkdWebViewClient() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    Scaffold(modifier = Modifier, topBar = {
        GkTopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                GkIconButton(
                    imageVector = GkIcons.ArrowBack,
                    onClick = { mainVm.popPage() },
                )
            },
            title = {
                val loadingState = webViewState.loadingState
                if (loadingState is LoadingState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.iconTextSize(),
                    )
                } else {
                    Text(
                        // webViewState.pageTitle becomes null after calling reload
                        text = webViewState.pageTitle ?: webView?.title ?: "",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                if (chromeVersion in 1..<MINI_CHROME_VERSION) {
                    GkIconButton(
                        imageVector = GkIcons.WarningAmber,
                        onClick = throttle(mainVm.scope.launchUiAction {
                            mainVm.dialogRequests.showMessage(
                                title = UiStrings.compatibility_notice,
                                text = UiStrings.webview_outdated_notice(chromeVersion),
                            )
                        }),
                    )
                }
                var expanded by remember { mutableStateOf(false) }
                GkIconButton(imageVector = GkIcons.MoreVert, onClick = { expanded = true })
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
                                    Text(text = UiStrings.webview_reload)
                                },
                                onClick = {
                                    expanded = false
                                    webView?.reload()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(text = UiStrings.link_copy)
                            },
                            onClick = {
                                expanded = false
                                copyText(webView?.url ?: initUrl)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = UiStrings.link_open_external)
                            },
                            onClick = {
                                expanded = false
                                IntentUtils.openUri(webView?.url ?: initUrl)
                            }
                        )
                    }
                }
            }
        )
    }) { contentPadding ->
        WebView(
            modifier = Modifier
                .fillMaxSize()
                .scaffoldPadding(contentPadding),
            state = webViewState,
            client = webViewClient,
            onCreated = {
                webView = it
                it.addJavascriptInterface(GkdWebViewJsApi, "gkd")
                it.settings.apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    if (AndroidTarget.TIRAMISU) {
                        setAlgorithmicDarkeningAllowed(false)
                    }
                }
            }
        )
    }
}

@Suppress("unused")
private object GkdWebViewJsApi {
    @JavascriptInterface
    fun getAppId() = META.appId

    @JavascriptInterface
    fun getAppName() = META.appName

    @JavascriptInterface
    fun getVersionCode() = META.versionCode

    @JavascriptInterface
    fun getVersionName() = META.versionName

    @JavascriptInterface
    fun getChannel() = META.channel

    @JavascriptInterface
    fun getDebuggable() = META.debuggable
}

private const val MINI_CHROME_VERSION = 107
private val chromeVersion by lazy {
    WebView.getCurrentWebViewPackage()?.versionName?.run {
        splitToSequence('.').first().toIntOrNull()
    } ?: 0
}

private const val DOC_CONFIG_URL =
    "https://registry.npmmirror.com/@gkd-kit/docs/latest/files/_config.json"

private const val DEBUG_JS_TEXT = """
<script src="https://registry.npmmirror.com/eruda/latest/files"></script>
<script>eruda.init();</script>
"""


@Serializable
private data class DocConfig(
    val mirrorBaseUrl: String,
    val htmlUrlMap: Map<String, String>
)

private class GkdWebViewClient() : AccompanistWebViewClient() {
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url
        if (uri != null && uri.host != "gkd.li") {
            if (uri.scheme == "gkd") {
                (view?.context as? MainActivity)?.mainVm?.handleGkdUri(uri)
            } else {
                IntentUtils.openUri(uri)
            }
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
