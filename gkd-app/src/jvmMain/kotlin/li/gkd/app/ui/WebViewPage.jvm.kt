package li.gkd.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import li.gkd.app.DesktopProfile
import li.gkd.app.DesktopStorage
import li.gkd.app.LocalDesktopRouteActive
import li.gkd.app.platform.writeClipboardText
import li.gkd.app.resources.Res
import li.gkd.app.resources.webview_content
import li.gkd.app.resources.webview_load_failed
import li.gkd.app.resources.webview_runtime_install
import li.gkd.app.storage.appStorage
import li.gkd.app.ui.component.GkWebViewErrorContent
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.page.WebViewScreen
import li.gkd.app.ui.platform.SystemActionFeedback
import li.gkd.app.ui.share.LocalDarkTheme
import li.songe.compose.webview2.JavascriptInterface
import li.songe.compose.webview2.WebView
import li.songe.compose.webview2.WebViewClient
import li.songe.compose.webview2.WebViewColorScheme
import li.songe.compose.webview2.WebViewNewWindowAction
import li.songe.compose.webview2.WebViewSettings
import li.songe.compose.webview2.rememberWebViewBindings
import li.songe.compose.webview2.rememberWebViewState
import org.jetbrains.compose.resources.stringResource
import java.awt.event.KeyEvent
import java.net.URI

private class DesktopWebViewBridge(initialDark: Boolean) {
    @Volatile
    var dark = initialDark

    @JavascriptInterface
    fun getEnvironment(): String = DesktopProfile.webViewEnvironmentJson

    @JavascriptInterface
    fun isDarkTheme(): Boolean = dark
}

private data class WebViewFailure(val url: String, val message: String)

@Composable
@Suppress("DEPRECATION")
actual fun WebViewPage(
    route: WebViewRoute,
    window: AppWindow,
    browsersRunning: () -> Boolean,
    onHostKey: (Int) -> Unit
) {
    val running = browsersRunning()
    val state = window.state
    val storage = appStorage()

    var attempt by remember(route) { mutableIntStateOf(0) }
    var retryUrl by remember(route) { mutableStateOf(route.initUrl) }
    key(route, attempt) {
        val browser = rememberWebViewState(retryUrl)
        val scope = rememberCoroutineScope()
        val dark = LocalDarkTheme.current
        val bridge = remember { DesktopWebViewBridge(dark) }
        SideEffect { bridge.dark = dark }
        val hostKey by rememberUpdatedState(onHostKey)
        val active by rememberUpdatedState(LocalDesktopRouteActive.current && running)
        val bindings = rememberWebViewBindings(bridge, route) {
            allowOrigin("https://gkd.li")
            // The selected document can read the same read-only environment API.
            runCatching { URI(route.initUrl) }.getOrNull()?.let { uri ->
                if (uri.scheme?.lowercase() in setOf(
                        "http",
                        "https"
                    ) && uri.host != null && uri.userInfo == null
                ) {
                    allowOrigin(
                        URI(
                            uri.scheme,
                            null,
                            uri.host,
                            uri.port,
                            null,
                            null,
                            null
                        ).toString()
                    )
                }
            }
            addJavascriptInterface(bridge, "gkd")
        }
        val client = remember(state, scope) {
            WebViewClient(
                onNavigationRequest = { request ->
                    when (runCatching { URI(request.url).scheme?.lowercase() }.getOrNull()) {
                        "gkd" -> {
                            scope.launch { state.handleGkdUri(request.url) }; false
                        }

                        "http", "https", "about", "data" -> true
                        else -> {
                            scope.launch { state.unsupported() }; false
                        }
                    }
                },
                onNewWindowRequest = { WebViewNewWindowAction.OpenInCurrentWebView },
                onAcceleratorKey = { event ->
                    val handled = active && event.virtualKey in setOf(
                        KeyEvent.VK_ESCAPE, KeyEvent.VK_F12,
                    )
                    if (handled && event.isKeyDown && !event.isRepeat) {
                        scope.launch { if (active) hostKey(event.virtualKey) }
                    }
                    handled
                },
            )
        }
        var error by remember { mutableStateOf<WebViewFailure?>(null) }
        LaunchedEffect(browser.error) {
            browser.error?.let { failure ->
                val details = buildList {
                    add("${failure.stage}: ${failure.message}")
                    failure.hresult?.let { add("HRESULT: 0x${it.toUInt().toString(16)}") }
                    failure.navigationErrorCode?.let { add("WebErrorStatus: $it") }
                    failure.processFailureKind?.let { add("ProcessFailedKind: $it") }
                }.joinToString("\n")
                error = WebViewFailure(failure.url ?: browser.url.ifBlank { retryUrl }, details)
            }
        }
        LaunchedEffect(dark, browser.frameCount > 0, running) {
            if (running && browser.frameCount > 0 && error == null) {
                try {
                    browser.evaluateJavaScript("window.dispatchEvent(new Event('gkd:themechange'));")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Navigation can invalidate the document; its replacement reads the current bridge.
                    System.err.println("WebView theme notification: ${e.message}")
                }
            }
        }
        val url = error?.url ?: browser.url.ifBlank { retryUrl }
        val retry: () -> Unit = { retryUrl = url; attempt++ }
        val contentLabel = stringResource(Res.string.webview_content)
        WebViewScreen(
            title = if (error != null) stringResource(Res.string.webview_load_failed) else browser.title,
            loading = error == null && browser.isLoading,
            onBack = state::popPage,
            onReload = { if (error != null) retry() else browser.reload() },
            onCopyLink = { writeClipboardText(url) },
            onOpenExternal = { SystemActionFeedback.openExternal(url, state.toast::show) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                WebView(
                    state = browser,
                    modifier = Modifier.fillMaxSize().alpha(if (error == null) 1f else 0f)
                        .semantics { contentDescription = contentLabel },
                    settings = WebViewSettings(
                        userDataDirectory = storage.webViewCache.absolutePath,
                        requestNativeFocus = !DesktopStorage.isolated,
                        preferredColorScheme = if (dark) WebViewColorScheme.Dark else WebViewColorScheme.Light,
                    ),
                    running = running && error == null,
                    bindings = bindings,
                    client = client,
                )
                if (error != null) {
                    GkWebViewErrorContent(
                        onRetry = retry,
                        onOpenExternal = {
                            SystemActionFeedback.openExternal(
                                url,
                                state.toast::show
                            )
                        },
                        url = url,
                        errorText = error?.message.orEmpty()
                    ) {
                        TextButton(onClick = {
                            runCatching {
                                SystemActionFeedback.openExternal(
                                    "https://developer.microsoft.com/microsoft-edge/webview2/",
                                    state.toast::show
                                )
                            }.onFailure { state.toast.show(it.message ?: it.toString()) }
                        }) { Text(stringResource(Res.string.webview_runtime_install)) }
                    }
                }
            }
        }
    }
}
