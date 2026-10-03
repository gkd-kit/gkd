package li.gkd.app

import li.gkd.app.permission.AndroidPermissions
import li.gkd.app.priv.PrivilegeCapabilities
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.settings.SettingsStore
import li.gkd.app.testing.TestFiles
import li.gkd.app.ui.home.DashboardPlatformState
import li.gkd.app.ui.home.DashboardPrivilegeStatus
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.WorkModeRoute
import priv.kit.ui.PrivilegeUiRuntimeStartSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivilegeRestrictionTest {
    private val allowed = PrivilegeCapabilities(true, true, true, true)
    private val automation = SettingsStore(automatorMode = AutomatorMode.Automation.value)

    private fun dashboard(capabilities: PrivilegeCapabilities?, secure: Boolean = true) =
        DashboardPlatformState(
            a11yRunning = false, automationRunning = false, a11yEnabled = false,
            writeSecureSettings = secure, partiallyDisabled = false, privilegeAvailable = true,
            privilegeStatus = DashboardPrivilegeStatus.Connected, statusRunning = false,
            activityRunning = false, restricted = false, topAppId = "example.app",
            privilegeCapabilities = capabilities,
        )

    @Test
    fun deniedInputBlocksStartButUnknownPermissionsAndStopRemainAvailable() {
        // Only a confirmed denial blocks the UI; starting performs the actual permission check.
        val denied = dashboard(allowed.copy(injectEvents = false))
        assertEquals(PrivilegeServiceRoute, denied.authorizationRoute(true, automation, emptySet()))
        assertNull(denied.authorizationRoute(false, automation, emptySet()))
        assertNull(dashboard(null).authorizationRoute(true, automation, emptySet()))
        assertNull(dashboard(allowed).authorizationRoute(true, automation, emptySet()))
    }

    @Test
    fun grantAndSecureSettingsRestrictionsDoNotInvalidateIndependentExecutionPaths() {
        // Previously granted app permission survives an ADB restriction; automation only needs input here.
        for (capabilities in listOf(
            allowed.copy(grantRuntimePermissions = false),
            allowed.copy(writeSecureSettings = false),
            allowed.copy(updateAppOps = false),
        )) {
            val state = dashboard(capabilities)
            assertNull(state.authorizationRoute(true, automation, emptySet()))
            assertNull(state.authorizationRoute(true, SettingsStore(), emptySet()))
            assertEquals(WorkModeRoute, state.copy(writeSecureSettings = false)
                .authorizationRoute(true, SettingsStore(), emptySet()))
        }
        // Scoped accessibility mode must keep using the app's permission even when injection is denied.
        assertNull(dashboard(allowed.copy(injectEvents = false))
            .authorizationRoute(true, automation, setOf("example.app")))
    }

    @Test
    fun inputRestrictionDoesNotStopExistingSimulatedAutomation() {
        val store = SimulatorStore(SimulatorSettings(
            permissions = SimulatedPermissions(writeSecureSettings = true),
            services = SimulatedServices(automationRunning = true),
            privilege = SimulatedPrivilege().withAvailability(true),
        ))
        store.update { it.copy(permissions = it.permissions.copy(
            deniedServerPermissions = setOf(AndroidPermissions.INJECT_EVENTS)
        )) }
        assertTrue(store.settings.value.services.automationRunning)
        assertTrue(store.settings.value.privilege.available)
        assertTrue(store.settings.value.permissions.writeSecureSettings)
        store.update { it.copy(permissions = it.permissions.copy(deniedServerPermissions = emptySet())) }
        assertTrue(store.settings.value.services.automationRunning)
        assertTrue(store.settings.value.privilegeCapabilities()!!.injectEvents)
    }

    @Test
    fun adbAndAppOpsRestrictionsPersistSeparatelyAndRootDoesNotInheritAdbDenials() {
        val directory = TestFiles.root.resolve("privilege-restrictions").apply { mkdirs() }
        val storage = DesktopSimulatorStorage(directory.resolve("simulator.json"))
        val store = SimulatorStore(SimulatorSettings(
            privilege = SimulatedPrivilege().withAvailability(true),
        ), storage::save)
        store.setRestricted(true)
        assertFalse(store.settings.value.privilegeCapabilities()!!.restricted)
        assertTrue(store.settings.value.privilegeUiState().deniedServerPermissions.isEmpty())
        store.update { it.copy(permissions = it.permissions.copy(
            restricted = false,
            deniedServerPermissions = setOf(AndroidPermissions.GRANT_RUNTIME_PERMISSIONS),
        )) }
        val restored = storage.load(DesktopEnvironment())
        assertFalse(restored.permissions.restricted)
        assertFalse(restored.privilegeCapabilities()!!.grantRuntimePermissions)
        assertTrue(restored.privilegeCapabilities()!!.injectEvents)
        val root = restored.copy(privilege = restored.privilege.copy(runtimeStartSource = PrivilegeUiRuntimeStartSource.ROOT))
        assertFalse(root.privilegeCapabilities()!!.restricted)
        assertTrue(root.privilegeUiState().deniedServerPermissions.isEmpty())
    }
}
