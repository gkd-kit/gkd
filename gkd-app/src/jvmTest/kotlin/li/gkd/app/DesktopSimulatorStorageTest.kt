package li.gkd.app

import priv.kit.ui.PrivilegeUiPermissionRestrictionStatus
import priv.kit.ui.PrivilegeUiRuntimeStartSource
import priv.kit.ui.PrivilegeUiRuntimeStatus
import priv.kit.ui.PrivilegeUiStartupMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSimulatorStorageTest {
    @Test
    fun restartRestoresRootAndPermissionsButDropsPendingOperationsAndIme() {
        val directory = Files.createTempDirectory("gkd-simulator-restart-").toFile()
        try {
            val storage = DesktopSimulatorStorage(directory.resolve("simulator.json"))
            val settings = SimulatorSettings(
                device = SimulatedDevice(imeVisible = true),
                permissions = SimulatedPermissions(restricted = true),
                privilege = SimulatedPrivilege(
                    runtimeStatus = PrivilegeUiRuntimeStatus.CONNECTED,
                    runtimeStartSource = PrivilegeUiRuntimeStartSource.ROOT,
                    selectedStartupMode = PrivilegeUiStartupMode.ROOT,
                    paired = true, externalAuthorized = true, activeTcpPort = 5555,
                    pairingDialogVisible = true, pairingCode = "123456", busy = true,
                    startupLogLines = listOf("[simulation] old session"),
                ),
            )
            storage.save(settings)
            val restored = storage.load(DesktopEnvironment())
            assertEquals(0, restored.privilege.serverUid)
            assertEquals(
                PrivilegeUiPermissionRestrictionStatus.NOT_RESTRICTED,
                restored.privilegeUiState().permissionRestrictionStatus
            )
            assertTrue(restored.privilege.paired)
            assertTrue(restored.privilege.externalAuthorized)
            assertEquals(5555, restored.privilege.activeTcpPort)
            assertFalse(restored.device.imeVisible)
            assertFalse(restored.privilege.busy)
            assertFalse(restored.privilege.pairingDialogVisible)
            assertEquals("", restored.privilege.pairingCode)
            assertTrue(restored.privilege.startupLogLines.isEmpty())
            storage.save(settings.copy(privilege = settings.privilege.copy(runtimeStatus = PrivilegeUiRuntimeStatus.STARTING)))
            assertFalse(storage.load(DesktopEnvironment()).privilege.available)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun configurationSurvivesRestartWithoutRestoringImeOrOverwritingInvalidInput() {
        val directory = Files.createTempDirectory("gkd-simulator-test").toFile()
        try {
            val file = directory.resolve("simulator.json")
            val storage = DesktopSimulatorStorage(file)
            val original = SimulatorSettings().withEnvironment(
                DesktopEnvironment(
                    fontScale = 1.5f,
                    android = AndroidWindow(imeVisible = true, batteryPercent = 42)
                )
            )
            storage.save(original)
            val reopened = DesktopSimulatorStorage(file).load(DesktopEnvironment())
            assertEquals(1.5f, reopened.device.fontScale)
            assertEquals(100, reopened.device.batteryPercent)
            assertFalse(reopened.device.imeVisible)
            assertFailsWith<IllegalArgumentException> {
                storage.save(
                    original.copy(
                        device = original.device.copy(
                            width = 0
                        )
                    )
                )
            }
            assertEquals(reopened, storage.load(DesktopEnvironment()))
            file.writeText("invalid")
            assertFails { storage.load(DesktopEnvironment()) }
            assertEquals("invalid", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
