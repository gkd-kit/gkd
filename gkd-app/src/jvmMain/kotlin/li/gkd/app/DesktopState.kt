package li.gkd.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.Serializable
import li.gkd.app.network.AppLinks
import li.gkd.app.platform.writeClipboardText
import li.gkd.app.resources.Res
import li.gkd.app.resources.uri_unknown
import li.gkd.app.ui.component.ToastState
import li.gkd.app.ui.home.BottomNavItem
import li.gkd.app.ui.home.HomeNavigation
import li.gkd.app.ui.navigation.A11YScopeAppListRoute
import li.gkd.app.ui.navigation.A11yEventLogRoute
import li.gkd.app.ui.navigation.AboutRoute
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.ActionToastRoute
import li.gkd.app.ui.navigation.ActivityLogRoute
import li.gkd.app.ui.navigation.AdvancedPageRoute
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.BlockA11yAppListRoute
import li.gkd.app.ui.navigation.BlockA11ySetupRoute
import li.gkd.app.ui.navigation.CategoryEditorRoute
import li.gkd.app.ui.navigation.CrashReportRoute
import li.gkd.app.ui.navigation.EditBlockAppListRoute
import li.gkd.app.ui.navigation.GkdLink
import li.gkd.app.ui.navigation.HomeRoute
import li.gkd.app.ui.navigation.ImagePreviewRoute
import li.gkd.app.ui.navigation.NotificationTextRoute
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.RuleExcludeEditorRoute
import li.gkd.app.ui.navigation.SnapshotPageRoute
import li.gkd.app.ui.navigation.SnapshotPreviewRoute
import li.gkd.app.ui.navigation.SnapshotSettingsRoute
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsAppListRoute
import li.gkd.app.ui.navigation.SubsCategoryGroupRoute
import li.gkd.app.ui.navigation.SubsCategoryRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupExcludeRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.navigation.WorkModeRoute
import li.gkd.app.ui.text.getSync
import li.gkd.db.LOCAL_SUBS_ID

@Serializable
data class DesktopEnvironment(
    val width: Int = defaultSimulatedDevice.width,
    val height: Int = defaultSimulatedDevice.height,
    val density: Float = defaultSimulatedDevice.density,
    val fontScale: Float = defaultSimulatedDevice.fontScale,
    val dark: Boolean = false,
    val locale: String = defaultSimulatedDevice.locale,
    val android: AndroidWindow = AndroidWindow(),
) {
    fun validate() {
        require(width in 240..1600 && height in 320..2400) { "Invalid viewport" }
        require(density in 0.5f..3f && fontScale in 0.5f..3f) { "Invalid density or fontScale" }
        require(locale in setOf("zh-CN", "en")) { "Unsupported development locale" }
        android.validate(width, height)
    }
}

@Serializable
data class ScenarioRequest(
    val page: String = "dashboard",
    val variant: String = "normal",
    val route: AppRoute? = null,
    val overlay: String? = null
)

@Serializable
data class DesktopSnapshot(
    val page: String,
    val variant: String,
    val revision: Int,
    val environment: DesktopEnvironment,
    val lastEvent: String?,
    val isolated: Boolean = false,
    val simulator: SimulatorSettings,
)

class DesktopState(
    initialEnvironment: DesktopEnvironment = DesktopEnvironment(),
    private val isolated: Boolean = false,
    val simulator: SimulatorStore = SimulatorStore(
        SimulatorSettings().withEnvironment(
            initialEnvironment
        )
    ),
) : ViewModelStoreOwner {
    var overlay by mutableStateOf<String?>(null)
    val toast = ToastState()
    fun unsupported() {
        toast.show("当前平台不支持")
    }

    val environment: DesktopEnvironment get() = simulator.settings.value.environment()
    private val privilegeScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val privilege = DesktopPrivilegeSimulation(
        scope = privilegeScope,
        store = simulator,
        copyText = {
            writeClipboardText(it)
        },
        onFailure = { toast.show("模拟设置保存失败：${it.message ?: it}") },
    )
    private var requestedScenario by mutableStateOf(ScenarioRequest())
    override val viewModelStore = ViewModelStore()
    var homeNavigation by mutableStateOf(HomeNavigation())
        private set

    val backStack = mutableStateListOf<NavKey>(HomeRoute)
    val scenario: ScenarioRequest
        get() = if (backStack.last() == HomeRoute) {
            requestedScenario.copy(page = homePages.getValue(homeNavigation.selectedTab.key))
        } else requestedScenario.copy(page = routeName(backStack.last()))
    var revision by mutableStateOf(0)
        private set
    var lastEvent by mutableStateOf<String?>(null)
        private set

    fun load(request: ScenarioRequest) {
        require(request.route != null || request.page in pages) { "Unknown page: ${request.page}" }
        require(request.variant in variants) { "Unknown variant: ${request.variant}" }
        viewModelStore.clear()
        homeNavigation = HomeNavigation()
        overlay = request.overlay
        requestedScenario = request
        backStack.clear()
        backStack.add(HomeRoute)
        if (request.route != null) backStack.add(request.route)
        else if (request.page !in homePages.values) backStack.add(routeFor(request.page))
        selectHomeTab(if (request.page == "edit-whitelist") "apps" else if (request.page in homePages.values) request.page else "settings")
        lastEvent = null
        revision++
    }

    fun navigate(route: AppRoute, replaced: Boolean = false) {
        if (replaced && backStack.size > 1) backStack.removeLast()
        if (backStack.last() != route) backStack.add(route)
    }

    fun navigate(page: String) {
        require(page in pages)
        if (page in homePages.values) {
            while (backStack.size > 1) backStack.removeLast()
            selectHomeTab(page)
        } else if (backStack.last() != routeFor(page)) {
            backStack.add(routeFor(page))
        }
    }

    fun dismissIme(): Boolean {
        if (!environment.android.imeVisible) return false
        simulator.updateAndroid { it.copy(imeVisible = false) }
        return true
    }

    fun popPage() {
        if (backStack.size > 1) backStack.removeLast()
    }

    fun handleGkdUri(uri: String) {
        when (val link = GkdLink.parse(uri)) {
            is GkdLink.Home -> {
                BottomNavItem.allSubObjects.firstOrNull { it.key == link.tab }
                    ?.let(homeNavigation::selectTab)
                while (backStack.size > 1) backStack.removeLast()
            }

            is GkdLink.Page -> navigate(link.route)
            GkdLink.WeChatScanner -> unsupported()
            null -> toast.show(Res.string.uri_unknown.getSync(uri))
        }
    }

    private fun selectHomeTab(page: String) {
        homePages.entries.firstOrNull { it.value == page }?.let { entry ->
            homeNavigation.selectTab(BottomNavItem.allSubObjects.first { it.key == entry.key })
        }
    }

    fun close() {
        viewModelStore.clear(); privilege.close(); privilegeScope.cancel()
    }

    fun simulatorCommand(action: () -> Unit) {
        try {
            action()
        } catch (e: IllegalArgumentException) {
            toast.show("模拟设置无效：${e.message ?: e}")
        } catch (e: Exception) {
            toast.show("模拟设置更新失败：${e.javaClass.simpleName}: ${e.message ?: e}")
        }
    }

    fun record(event: String) {
        lastEvent = event
    }

    fun snapshot(): DesktopSnapshot {
        val settings = simulator.settings.value
        return DesktopSnapshot(
            scenario.page,
            scenario.variant,
            revision,
            settings.environment(),
            lastEvent,
            isolated,
            settings
        )
    }

    companion object {
        private val homePages by lazy {
            mapOf(
                BottomNavItem.Dashboard.key to "dashboard",
                BottomNavItem.SubsManage.key to "subscriptions",
                BottomNavItem.AppList.key to "apps",
                BottomNavItem.Settings.key to "settings",
            )
        }
        val pages by lazy { homePages.values + (desktopRoutes.keys - "home") }
        val variants = listOf("normal", "empty")
    }
}

private data object ComponentCatalogRoute : NavKey

private val desktopRoutes by lazy {
    mapOf(
        "home" to HomeRoute,
        "action-toast" to ActionToastRoute,
        "notification-text" to NotificationTextRoute,
        "edit-whitelist" to EditBlockAppListRoute,
        "about" to AboutRoute,
        "block-a11y-setup" to BlockA11ySetupRoute,
        "work-mode" to WorkModeRoute,
        "snapshot-settings" to SnapshotSettingsRoute,
        "advanced" to AdvancedPageRoute,
        "crash-reports" to CrashReportRoute,
        "activity-log" to ActivityLogRoute,
        "event-log" to A11yEventLogRoute,
        "rule-editor" to UpsertRuleGroupRoute(LOCAL_SUBS_ID),
        "category-editor" to CategoryEditorRoute(LOCAL_SUBS_ID),
        "subs-global-groups" to SubsGlobalGroupListRoute(LOCAL_SUBS_ID),
        "subs-apps" to SubsAppListRoute(LOCAL_SUBS_ID),
        "subs-app-groups" to SubsAppGroupListRoute(LOCAL_SUBS_ID, "li.gkd"),
        "subs-categories" to SubsCategoryRoute(LOCAL_SUBS_ID),
        "subs-category-groups" to SubsCategoryGroupRoute(LOCAL_SUBS_ID, 0),
        "subs-global-exclude" to SubsGlobalGroupExcludeRoute(LOCAL_SUBS_ID, 0),
        "rule-exclude-editor" to RuleExcludeEditorRoute(LOCAL_SUBS_ID, 0),
        "a11y-scope-apps" to A11YScopeAppListRoute,
        "block-a11y-apps" to BlockA11yAppListRoute,
        "app-config" to AppConfigRoute("li.gkd"),
        "action-log" to ActionLogRoute(),
        "image-preview" to ImagePreviewRoute(),
        "snapshots" to SnapshotPageRoute,
        "snapshot-preview" to SnapshotPreviewRoute(0, emptyList()),
        "web-view" to WebViewRoute(AppLinks.Home),
        "privilege-service" to PrivilegeServiceRoute,
        "components" to ComponentCatalogRoute,
    )
}

fun routeFor(page: String): NavKey = desktopRoutes.getValue(page)
fun routeName(route: NavKey): String = when (route) {
    is SubsGlobalGroupListRoute -> "subs-global-groups"
    is SubsAppListRoute -> "subs-apps"
    is SubsAppGroupListRoute -> "subs-app-groups"
    is SubsCategoryRoute -> "subs-categories"
    is SubsCategoryGroupRoute -> "subs-category-groups"
    is SubsGlobalGroupExcludeRoute -> "subs-global-exclude"
    is RuleExcludeEditorRoute -> "rule-exclude-editor"
    is WebViewRoute -> "web-view"
    is SnapshotPreviewRoute -> "snapshot-preview"
    is ImagePreviewRoute -> "image-preview"
    is AppConfigRoute -> "app-config"
    is ActionLogRoute -> "action-log"
    is UpsertRuleGroupRoute -> "rule-editor"
    is CategoryEditorRoute -> "category-editor"
    else -> desktopRoutes.entries.first { it.value == route }.key
}
