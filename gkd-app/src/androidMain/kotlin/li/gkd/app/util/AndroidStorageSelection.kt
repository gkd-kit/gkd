package li.gkd.app.util

import java.io.File
import li.gkd.app.storage.StorageSelectionReason
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.db.SubscriptionIds

data class StorageRootSelection(
    val selected: File,
    val reason: StorageSelectionReason,
    val createMarker: Boolean,
    val removeMarker: Boolean,
)

private fun File.hasAcceptedTerms(): Boolean = resolve("store/terms_accepted.txt").isFile

private fun File.subscriptionFiles(): List<File> =
    resolve("subscription").listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()

/**
 * `SubscriptionRepository` creates `subscription/-2.json` on first launch. It has version 0 and
 * no rules, so it must not be treated as user data when checking whether the internal root is empty.
 */
private fun File.hasRealSubscriptionData(): Boolean =
    subscriptionFiles().any { file ->
        val id = file.nameWithoutExtension.toLongOrNull() ?: return@any true
        if (!SubscriptionIds.isLocal(id)) return@any true
        !isPlaceholderLocalSubscription(file)
    }

private fun isPlaceholderLocalSubscription(file: File): Boolean {
    // The placeholder is only a few dozen bytes. Skip parsing large local subscriptions during startup.
    if (file.length() > PLACEHOLDER_MAX_BYTES) return false
    val text = runCatching { file.readText() }.getOrNull() ?: return false
    val subscription =
        runCatching { RawSubscriptionParser.parse(text, json5 = false) }.getOrNull() ?: return false
    return subscription.version == 0 && !subscription.hasRule && subscription.categories.isEmpty()
}

private const val PLACEHOLDER_MAX_BYTES = 512L

/**
 * Selects the data root.
 *
 * The `.gkd` marker in the internal files directory means "always use internal storage". That is
 * fine when external storage is really gone, but `getExternalFilesDir` also returns null for a
 * short time during boot, before the user unlocks, or after a soft reboot. The old code wrote the
 * marker immediately in those cases and never looked at the external directory again, so the app
 * started with an empty internal root while the old data was still on external storage.
 *
 * Two guards here:
 * - only write the marker when the user is unlocked and external storage is really unavailable;
 * - if the marker exists but the internal root has no real subscriptions while the external root
 *   has user data, treat the marker as accidental, remove it and switch back.
 */
fun selectStorageRoot(
    internalFiles: File,
    external: File?,
    markerExists: Boolean,
    userUnlocked: Boolean,
    externalPermanentlyUnavailable: Boolean,
): StorageRootSelection {
    val internalHasTerms = internalFiles.hasAcceptedTerms()
    val internalHasRealSubscriptions = internalFiles.hasRealSubscriptionData()
    val externalHasTerms = external?.hasAcceptedTerms() == true
    val externalHasSubscriptions = external?.hasRealSubscriptionData() == true
    val externalHasData = externalHasTerms || externalHasSubscriptions
    val shouldRecover =
        markerExists &&
            externalHasData &&
            !internalHasRealSubscriptions &&
            (!internalHasTerms || externalHasSubscriptions)
    if (shouldRecover) {
        return StorageRootSelection(
            selected = checkNotNull(external),
            reason = StorageSelectionReason.RecoveredFromExternal,
            createMarker = false,
            removeMarker = true,
        )
    }
    if (markerExists) {
        return StorageRootSelection(
            selected = internalFiles,
            reason = StorageSelectionReason.InternalMarkerExists,
            createMarker = false,
            removeMarker = false,
        )
    }
    if (external != null) {
        return StorageRootSelection(
            selected = external,
            reason = StorageSelectionReason.ExternalAvailable,
            createMarker = false,
            removeMarker = false,
        )
    }
    return StorageRootSelection(
        selected = internalFiles,
        reason = StorageSelectionReason.ExternalNullFallback,
        // A transient null (locked user, USB sharing, mounting) must not be persisted.
        createMarker = userUnlocked && externalPermanentlyUnavailable,
        removeMarker = false,
    )
}
