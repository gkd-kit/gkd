package li.gkd.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_toast
import li.gkd.app.resources.action_toast_enable
import li.gkd.app.resources.notification_text
import li.gkd.app.resources.settings_title
import li.gkd.app.resources.webview_shutdown_pending
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTermsAcceptDialog
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkToastHost
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.GkTriStateSwitch
import li.gkd.app.ui.component.LocalOverlayBackHandler
import li.gkd.app.ui.icon.Logo
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.share.LocalIsTalkbackEnabled
import li.gkd.app.ui.theme.GkTheme
import li.gkd.app.ui.theme.rememberAppearance
import li.gkd.app.window.DesktopWindowGeometry
import li.gkd.app.window.GkDesktopWindowFrame
import li.songe.compose.webview2.WebViewDiagnostics
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.awt.Frame
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

fun runDesktop(args: Array<String>) {
    require(args.all { it == "--test" }) { "Only --test is supported" }
    DesktopStorage.initialize("--test" in args)
    val testWindows = if (DesktopStorage.isolated) DesktopTestWindows.install() else null
    application {
        DisposableEffect(Unit) { onDispose { testWindows?.close() } }
        val windowIcon = rememberVectorPainter(GkIcons.Logo)
        val hostScope = rememberCoroutineScope()
        val persistence =
            remember { SimulatorPersistence(hostScope, environment = DesktopProfile.environment) }
        val simulator = persistence.store
        LaunchedEffect(simulator) {
            while (true) {
                val percent =
                    withContext(Dispatchers.IO) { DesktopBattery.readPercent() }
                simulator.updateHostBattery(percent)
                delay(30_000)
            }
        }
        val runtime =
            remember { DesktopRuntime(simulator) }
        val state =
            remember { DesktopState(isolated = DesktopStorage.isolated, simulator = simulator) }
        val tasks = remember { DesktopTasks(state) }
        var appWindow by remember { mutableStateOf<ComposeWindow?>(null) }
        val session = remember(state.revision) {
            DesktopSession(
                state,
                runtime,
                tasks,
                fileDialogOwner = { requireNotNull(appWindow) }
            )
        }
        val initialSize = remember { simulator.settings.value.device }
        val testPosition =
            if (DesktopStorage.isolated) WindowPosition.Absolute(
                (-10000).dp,
                (-10000).dp
            )
            else WindowPosition.PlatformDefault
        val windowState = rememberWindowState(
            width = (initialSize.width * initialSize.density).dp,
            height = (initialSize.height * initialSize.density).dp, position = testPosition
        )
        var appReady by remember { mutableStateOf(false) }
        val backDispatcher = remember { DesktopBackDispatcher() }
        val keyDispatcher = remember { DesktopKeyDispatcher() }
        val controlsWindowState =
            rememberWindowState(width = 460.dp, height = 820.dp, position = testPosition)
        var controlsVisible by remember { mutableStateOf(false) }
        var controlsWindow by remember { mutableStateOf<ComposeWindow?>(null) }
        val openControls: () -> Unit = {
            controlsWindowState.isMinimized = false
            controlsVisible = true
            if (!DesktopStorage.isolated) {
                controlsWindow?.let {
                    it.extendedState = it.extendedState and Frame.ICONIFIED.inv()
                    it.toFront()
                    it.requestFocus()
                }
            }
        }
        val flushSettings: () -> Unit = { hostScope.launch { persistence.flush() } }
        val closeControls: () -> Unit = { flushSettings(); controlsVisible = false }
        var closeAttempt by remember { mutableIntStateOf(0) }
        var browsersStopped by remember { mutableStateOf(false) }
        val closing = closeAttempt > 0
        val requestExit: () -> Unit = {
            hostScope.launch {
                if (persistence.flush() && !persistence.status.value.pending) {
                    closeAttempt++
                } else {
                    openControls()
                    state.toast.show("模拟设置尚未保存，请在控制窗口重试保存后退出")
                }
            }
        }
        val handleHostKey: (String) -> Boolean = { key ->
            when (key) {
                "F12" -> {
                    openControls()
                    true
                }

                "Escape" -> state.dismissIme() || backDispatcher.dispatch()
                else -> false
            }
        }
        var renderedRevision by remember { mutableStateOf(-1) }
        var appFrameClock by remember { mutableStateOf<MonotonicFrameClock?>(null) }
        DisposableEffect(session) { onDispose { session.scope.coroutineContext[Job]?.cancel() } }
        DisposableEffect(Unit) { onDispose { persistence.close(); tasks.close(); state.close(); runtime.close() } }
        DisposableEffect(appWindow) {
            val host = appWindow
            val server = host?.let {
                DesktopDebugServer.start(
                    state,
                    runtime,
                    host,
                    controlsWindow = { controlsWindow },
                    hostKey = handleHostKey,
                    awaitFrame = {
                        snapshotFlow { renderedRevision == state.revision }.first { it }
                        appFrameClock?.let { clock -> withContext(clock) { withFrameNanos { }; withFrameNanos { } } }
                    })
            }
            onDispose { server?.close() }
        }
        if (controlsVisible) {
            var controlsReady by remember { mutableStateOf(false) }
            Window(
                onCloseRequest = closeControls,
                title = "模拟设置",
                state = controlsWindowState,
                visible = controlsReady,
                icon = windowIcon,
                focusable = !DesktopStorage.isolated
            ) {
                SideEffect { controlsWindow = window }
                DisposableEffect(window) { onDispose { controlsWindow = null } }
                val simulatorSettings by simulator.settings.collectAsStateWithLifecycle()
                val systemDark = simulatorSettings.environment().dark
                CompositionLocalProvider(LocalDarkTheme provides systemDark) {
                    GkTheme(
                        colorScheme = DesktopProfile.colors(systemDark, true),
                        typography = DesktopProfile.typography
                    ) {
                        GkDesktopWindowFrame(
                            window, controlsWindowState, "模拟设置", systemDark,
                            onCloseRequest = closeControls, onReady = { controlsReady = true }) {
                            DesktopControls(
                                state,
                                session,
                                persistence,
                                flushSettings,
                                onBack = { (state.dismissIme() || backDispatcher.dispatch()) })
                        }
                    }
                }
            }
        }
        Window(
            onCloseRequest = requestExit,
            title = "GKD",
            onKeyEvent = { event ->
                !backDispatcher.hasOverlay && keyDispatcher.dispatch(event)
            },
            state = windowState,
            visible = appReady,
            icon = windowIcon,
            focusable = !DesktopStorage.isolated
        ) {
            LaunchedEffect(closeAttempt) {
                if (closing) {
                    val released = withTimeoutOrNull(5000) {
                        // The scene acknowledges running=false after its DisposableEffects are applied.
                        while (!browsersStopped) delay(20)
                        // A failed native library load cannot have created browser hosts.
                        while (runCatching { WebViewDiagnostics.activeNativeHosts }.getOrDefault(
                                0
                            ) != 0
                        ) {
                            delay(20)
                        }
                        true
                    } == true
                    if (released) {
                        println("GKD Desktop browser teardown complete")
                        exitApplication()
                    } else state.toast.show(getString(Res.string.webview_shutdown_pending))
                }
            }
            val simulatorSettings by simulator.settings.collectAsStateWithLifecycle()
            val size = simulatorSettings.environment()
            val appearance by rememberAppearance()
            val darkTheme = appearance.isDark(size.dark)
            val scope = rememberCoroutineScope()
            val platformDensity = LocalDensity.current.density
            val hostKeyHandler by rememberUpdatedState<(Int) -> Boolean>({ key ->
                when (key) {
                    KeyEvent.VK_F12 -> handleHostKey("F12")
                    KeyEvent.VK_ESCAPE -> handleHostKey("Escape")
                    else -> false
                }
            })
            DisposableEffect(window) {
                val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                val backKeys = KeyEventDispatcher { event ->
                    if (focusManager.focusedWindow === window && event.id == KeyEvent.KEY_PRESSED) {
                        hostKeyHandler(event.keyCode)
                    } else false
                }
                focusManager.addKeyEventDispatcher(backKeys)
                onDispose { focusManager.removeKeyEventDispatcher(backKeys) }
            }
            SideEffect {
                appWindow = window
                appFrameClock = scope.coroutineContext[MonotonicFrameClock]
            }

            LaunchedEffect(appReady, size.width, size.height, size.density, platformDensity) {
                if (appReady) {
                    withFrameNanos { }
                    val insets = window.insets
                    val titleHeight =
                        if (DesktopWindowGeometry.isCustom(window)) 32 else 0
                    windowState.size = DpSize(
                        (size.width * size.density + insets.left + insets.right).dp,
                        (size.height * size.density + insets.top + insets.bottom + titleHeight).dp,
                    )
                }
            }
            GkTheme(
                colorScheme = DesktopProfile.colors(
                    darkTheme,
                    appearance.dynamicColor
                ), typography = DesktopProfile.typography
            ) {
                GkDesktopWindowFrame(
                    window, windowState, "GKD", darkTheme,
                    onCloseRequest = requestExit,
                    viewportSize = if (appReady) null else DpSize(
                        (size.width * size.density).dp,
                        (size.height * size.density).dp
                    ),
                    onReady = { appReady = true }, onOpenControls = openControls
                ) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            platformDensity * size.density,
                            size.fontScale
                        ),
                        LocalDarkTheme provides darkTheme,
                        LocalIsTalkbackEnabled provides false,
                        LocalDesktopBackDispatcher provides backDispatcher,
                        LocalDesktopKeyDispatcher provides keyDispatcher,
                        LocalOverlayBackHandler provides { dismiss -> GkDesktopBackHandler(overlay = true, onBack = dismiss) },
                    ) {
                        // The JVM resource environment reads Locale.getDefault(); recreate resource consumers on language changes.
                        key(size.locale) {
                            Surface(Modifier.fillMaxSize()) {
                                GkAndroidWindow(
                                    size.copy(dark = darkTheme),
                                    onBack = { if (!state.dismissIme()) backDispatcher.dispatch() },
                                    onHome = { state.navigate("dashboard") },
                                    onRecents = state::unsupported,
                                ) {
                                    key(session.revision) {
                                        DesktopContent(session, !closing) { hostKeyHandler(it) }
                                        // Window content and application sessions recompose independently.
                                        // Acknowledge the session actually rendered, not the latest requested revision.
                                        val revision = session.revision
                                        SideEffect {
                                            renderedRevision = revision
                                            browsersStopped = closing
                                        }
                                    }
                                    val termsAccepted by SettingsRepository.termsAccepted.collectAsStateWithLifecycle()
                                    if (!termsAccepted) {
                                        GkTermsAcceptDialog(
                                            onAccept = SettingsRepository::acceptTerms,
                                            onError = {
                                                state.toast.show(
                                                    it.message ?: it.toString()
                                                )
                                            },
                                            onDisagree = requestExit,
                                        )
                                    } else {
                                        DesktopOverlays(state, session)
                                        session.dialogs.Render()
                                        session.textDialog.Render()
                                        session.links.Render()
                                        session.subsSheet.Render()
                                        session.rules.Render(
                                            onNavigate = { state.navigate(it) },
                                            showToast = state.toast::show,
                                            topRoute = { state.backStack.last() },
                                            openSubscription = session.subsSheet::show,
                                            ruleControl = session.ruleControl,
                                            confirmDelete = session::confirmDelete,
                                            copyText = {
                                                li.gkd.app.ui.navigation.copyText(
                                                    it,
                                                    state.toast::show
                                                )
                                            },
                                        )
                                        session.ruleControl.Render()
                                    }
                                    GkToastHost(state.toast, overlayHost = { toastContent ->
                                        Popup(
                                            alignment = Alignment.BottomCenter,
                                            properties = PopupProperties(
                                                focusable = false
                                            ),
                                        ) { toastContent() }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun DesktopContent(session: DesktopSession, running: Boolean, onBrowserKey: (Int) -> Unit) {
    val state = session.state
    val browsersRunning by rememberUpdatedState(running)
    val browserKey by rememberUpdatedState(onBrowserKey)
    GkDesktopBackHandler { state.popPage() }
    li.gkd.app.ui.navigation.GkAppNavigation(
        homeNavigation = state.homeNavigation,
        subsSheet = session.subsSheet,
        subsLinks = session.links,
        backStack = state.backStack,
        onBack = state::popPage,
        window = session,
        onNavigate = { state.navigate(it) },
        replaceRoute = { state.navigate(it, true) },
        showToast = state.toast::show,
        showText = session.textDialog::showText,
        topRoute = { state.backStack.last() },
        updateStatus = session.updateStatus,
        onExportLogs = { session.showShareLogs = true },
        takeCrashDataList = { emptyList() },
        dialogs = session.dialogs,
        ruleGroups = session.rules,
        githubUpload = session.githubUpload,
        confirmDelete = session::confirmDelete,
        scope = session.scope,
        browsersRunning = { browsersRunning },
        onBrowserKey = { browserKey(it) },
        entryContainer = { route, content ->
            CompositionLocalProvider(LocalDesktopRouteActive provides (state.backStack.last() == route)) { content() }
        },
        diagnosticContent = { route ->
            check(routeName(route) == "components") { "Unknown diagnostic route: $route" }
            ComponentCatalog(state)
        },
    )
}


@Composable
private fun ComponentCatalog(state: DesktopState) {
    var checked by remember { mutableStateOf(true) }
    var triState by remember { mutableStateOf<Boolean?>(null) }
    Scaffold(topBar = { GkTopAppBar(title = { Text("GKD · UI Playground") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            GkSettingItem(
                stringResource(Res.string.settings_title),
                onClick = { state.load(ScenarioRequest("settings")) })
            GkSettingItem(
                stringResource(Res.string.action_toast),
                onClick = { state.load(ScenarioRequest("action-toast")) })
            GkSettingItem(
                stringResource(Res.string.notification_text),
                onClick = { state.load(ScenarioRequest("notification-text")) })
            GkTextSwitch(
                title = stringResource(Res.string.action_toast_enable), checked = checked,
                onCheckedChange = { checked = it; state.record("switch: $it") })
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GkIcon(GkIcons.Android)
                GkIcon(GkIcons.Settings)
                GkTriStateSwitch(checked = triState, onCheckedChange = { triState = it })
            }
            if (state.scenario.variant == "empty") GkEmptyState()
            state.lastEvent?.let { Text(it, Modifier.padding(16.dp)) }
            GkPageBottomSpace()
        }
    }
}
