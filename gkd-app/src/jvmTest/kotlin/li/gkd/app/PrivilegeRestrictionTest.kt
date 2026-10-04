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
    fun unknownAndAllowedPermissionsDoNotBlockAutomationStart() {
        // Only a confirmed denial blocks the UI; starting performs the actual permission check.
        assertNull(dashboard(null).authorizationRoute(true, automation, emptySet()))
        assertNull(dashboard(allowed).authorizationRoute(true, automation, emptySet()))
    }

    @Test
    fun anyServerRestrictionBlocksAutomationStartButKeepsStopAndAccessibilityAvailable() {
        // New automation requires an unrestricted server; accessibility uses previously granted app permissions.
        for (capabilities in listOf(
            allowed.copy(injectEvents = false),
            allowed.copy(grantRuntimePermissions = false),
            allowed.copy(writeSecureSettings = false),
            allowed.copy(updateAppOps = false),
        )) {
            val state = dashboard(capabilities)
            assertEquals(PrivilegeServiceRoute, state.authorizationRoute(true, automation, emptySet()))
            assertNull(state.authorizationRoute(false, automation, emptySet()))
            assertNull(state.copy(automationRunning = true)
                .authorizationRoute(false, automation, emptySet()))
            assertNull(state.authorizationRoute(true, automation, setOf("example.app")))
            assertNull(state.authorizationRoute(true, SettingsStore(), emptySet()))
            assertEquals(WorkModeRoute, state.copy(writeSecureSettings = false)
                .authorizationRoute(true, SettingsStore(), emptySet()))
        }
    }

    @Test
    fun legacyAppOpsPermissionDenialDoesNotBlockModernSimulatedAutomation() {
        // Android 9+ setMode requires MANAGE_APP_OPS_MODES, not UPDATE_APP_OPS_STATS.
        val settings = SimulatorSettings(
            permissions = SimulatedPermissions(
                deniedServerPermissions = setOf(AndroidPermissions.UPDATE_APP_OPS_STATS),
            ),
            privilege = SimulatedPrivilege().withAvailability(true),
        )
        val capabilities = settings.privilegeCapabilities()!!
        assertTrue(capabilities.updateAppOps)
        assertFalse(capabilities.restricted)
        assertNull(dashboard(capabilities).authorizationRoute(true, automation, emptySet()))
    }

    @Test
    fun serverRestrictionsDoNotStopExistingSimulatedAutomation() {
        val store = SimulatorStore(SimulatorSettings(
            permissions = SimulatedPermissions(writeSecureSettings = true),
            services = SimulatedServices(automationRunning = true),
            privilege = SimulatedPrivilege().withAvailability(true),
        ))
        for (permission in listOf(
            AndroidPermissions.INJECT_EVENTS,
            AndroidPermissions.GRANT_RUNTIME_PERMISSIONS,
            AndroidPermissions.WRITE_SECURE_SETTINGS,
            AndroidPermissions.MANAGE_APP_OPS_MODES,
        )) {
            store.update { it.copy(permissions = it.permissions.copy(
                deniedServerPermissions = setOf(permission)
            )) }
            val capabilities = store.settings.value.privilegeCapabilities()!!
            assertTrue(capabilities.restricted)
            assertEquals(PrivilegeServiceRoute, dashboard(capabilities)
                .authorizationRoute(true, automation, emptySet()))
            assertTrue(store.settings.value.services.automationRunning)
            assertTrue(store.settings.value.privilege.available)
            assertTrue(store.settings.value.permissions.writeSecureSettings)
        }
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
