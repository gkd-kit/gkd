package li.gkd.app.util

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import li.gkd.app.storage.StorageSelectionReason

class AndroidStorageSelectionTest {
    private val roots = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        roots.forEach { it.deleteRecursively() }
    }

    private fun newRoot(): File =
        Files.createTempDirectory("gkd-storage-selection").toFile().also { roots += it }

    private fun File.withAcceptedTerms(): File =
        apply {
            resolve("store").mkdirs()
            resolve("store/terms_accepted.txt").writeText("true")
        }

    private fun File.withPlaceholderLocalSubscription(): File =
        apply {
            resolve("subscription").mkdirs()
            resolve("subscription/-2.json")
                .writeText(
                    """{"id":-2,"name":"本地订阅","version":0,"globalGroups":[],"categories":[],"apps":[]}"""
                )
        }

    private fun File.withRealLocalSubscription(): File =
        apply {
            resolve("subscription").mkdirs()
            resolve("subscription/-2.json")
                .writeText(
                    """{"id":-2,"name":"本地订阅","version":1,"globalGroups":[],"categories":[],"apps":[]}"""
                )
        }

    private fun File.withRemoteSubscription(id: Long): File =
        apply {
            resolve("subscription").mkdirs()
            resolve("subscription/$id.json")
                .writeText(
                    """{"id":$id,"name":"第三方订阅","version":1,"globalGroups":[],"categories":[],"apps":[]}"""
                )
        }

    @Test
    fun externalAvailableIsPreferred() {
        val internal = newRoot()
        val external = newRoot()
        val selection =
            selectStorageRoot(
                internalFiles = internal,
                external = external,
                markerExists = false,
                userUnlocked = true,
                externalPermanentlyUnavailable = false,
            )
        assertEquals(external, selection.selected)
        assertEquals(StorageSelectionReason.ExternalAvailable, selection.reason)
        assertFalse(selection.createMarker)
        assertFalse(selection.removeMarker)
    }

    @Test
    fun internalMarkerIsKeptWhenInternalHasUserData() {
        val internal = newRoot().withAcceptedTerms()
        val external = newRoot().withAcceptedTerms()
        val selection =
            selectStorageRoot(
                internalFiles = internal,
                external = external,
                markerExists = true,
                userUnlocked = true,
                externalPermanentlyUnavailable = false,
            )
        assertEquals(internal, selection.selected)
        assertEquals(StorageSelectionReason.InternalMarkerExists, selection.reason)
        assertFalse(selection.removeMarker)
    }

    @Test
    fun accidentalInternalMarkerIsRecoveredToExternalData() {
        val internal = newRoot()
        val external = newRoot().withAcceptedTerms()
        val selection =
            selectStorageRoot(
                internalFiles = internal,
                external = external,
                markerExists = true,
                userUnlocked = true,
                externalPermanentlyUnavailable = false,
            )
        assertEquals(external, selection.selected)
        assertEquals(StorageSelectionReason.RecoveredFromExternal, selection.reason)
        assertTrue(selection.removeMarker)
        assertFalse(selection.createMarker)
    }

    @Test
    fun recoversAfterTermsWereAcceptedInEmptyInternalRoot() {
        // 误切后用户在空目录里重新点了隐私协议：内部有 terms 但没有订阅，外部仍有订阅。
        val internal = newRoot().withAcceptedTerms()
        val external = newRoot().withAcceptedTerms().withRealLocalSubscription()
        val selection =
            selectStorageRoot(
                internalFiles = internal,
                external = external,
                markerExists = true,
                userUnlocked = true,
                externalPermanentlyUnavailable = false,
            )
        assertEquals(external, selection.selected)
        assertEquals(StorageSelectionReason.RecoveredFromExternal, selection.reason)
        assertTrue(selection.removeMarker)
    }

    @Test
    fun recoversWhenInternalOnlyHasAutoCreatedPlaceholderSubscription() {
        // 内部目录启动过一次后会自动生成 -2.json（version 0、无规则），它不能挡住恢复。
        val internal = newRoot().withAcceptedTerms().withPlaceholderLocalSubscription()
        val external = newRoot().withAcceptedTerms().withRemoteSubscription(233)
        val selection =
            selectStorageRoot(
                internalFiles = internal,
                external = external,
                markerExists = true,
                userUnlocked = true,
                externalPermanentlyUnavailable = false,
            )
        assertEquals(external, selection.selected)
        assertEquals(StorageSelectionReason.RecoveredFromExternal, selection.reason)
        assertTrue(selection.removeMarker)
    }

    @Test
    fun keepsInternalOnceItHasItsOwnRules() {
        val internal = newRoot().withAcceptedTerms().withRealLocalSubscription()
        val external = newRoot().withAcceptedTerms().withRemoteSubscription(233)
        val selection =
            selectStorageRoot(
                internalFiles = internal,
                external = external,
                markerExists = true,
                userUnlocked = true,
                externalPermanentlyUnavailable = false,
            )
        assertEquals(internal, selection.selected)
        assertEquals(StorageSelectionReason.InternalMarkerExists, selection.reason)
        assertFalse(selection.removeMarker)
    }

    @Test
    fun markerIsOnlyCreatedWhenExternalIsReallyUnavailable() {
        val internal = newRoot()
        val unlockedUnavailable =
            selectStorageRoot(
                internalFiles = internal,
                external = null,
                markerExists = false,
                userUnlocked = true,
                externalPermanentlyUnavailable = true,
            )
        assertTrue(unlockedUnavailable.createMarker)

        val locked =
            selectStorageRoot(
                internalFiles = internal,
                external = null,
                markerExists = false,
                userUnlocked = false,
                externalPermanentlyUnavailable = true,
            )
        assertFalse(locked.createMarker)
        assertEquals(StorageSelectionReason.ExternalNullFallback, locked.reason)

        val transientNull =
            selectStorageRoot(
                internalFiles = internal,
                external = null,
                markerExists = false,
                userUnlocked = true,
                externalPermanentlyUnavailable = false,
            )
        assertFalse(transientNull.createMarker)
    }
}
