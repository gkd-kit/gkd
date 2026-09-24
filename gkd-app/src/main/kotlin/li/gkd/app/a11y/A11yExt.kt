package li.gkd.app.a11y

import android.content.ComponentName
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.app
import li.gkd.app.contentObserver
import li.gkd.app.service.A11yService
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.mapState
import li.gkd.selector.createDefaultSelectorTypeModel
import kotlin.contracts.contract

fun useEnabledA11yServicesFlow(scope: CoroutineScope): StateFlow<Set<ComponentName>> {
    val initialValue = app.getSecureA11yServices()
    return callbackFlow {
        val contextObserver = contentObserver {
            trySend(app.getSecureA11yServices())
        }
        app.registerObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            contextObserver
        )
        trySend(initialValue)
        awaitClose { app.unregisterObserver(contextObserver) }
    }.stateIn(scope, SharingStarted.Eagerly, initialValue)
}

fun useA11yServiceEnabledFlow(
    scope: CoroutineScope,
    servicesFlow: StateFlow<Set<ComponentName>> = useEnabledA11yServicesFlow(scope),
): StateFlow<Boolean> {
    return servicesFlow.mapState(scope) {
        it.contains(A11yService.a11yCn)
    }
}

const val STATE_CHANGED = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
const val CONTENT_CHANGED = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

// Some apps take 300ms
private val AccessibilityEvent.safeSource: AccessibilityNodeInfo?
    get() = if (className == null) {
        null // https://github.com/gkd-kit/gkd/issues/426 event.clear has been called by the system
    } else {
        try {
            source?.setGeneratedTime()
        } catch (_: Exception) {
            // Unknown reason, still throws error Cannot perform this action on a not sealed instance.
            null
        }
    }

fun AccessibilityNodeInfo.getVid(): CharSequence? {
    val id = viewIdResourceName ?: return null
    val appId = packageName ?: return null
    if (id.startsWith(appId) && id.startsWith(":id/", appId.length)) {
        return id.subSequence(
            appId.length + ":id/".length,
            id.length
        )
    }
    return null
}

// https://github.com/gkd-kit/gkd/issues/115
// https://github.com/gkd-kit/gkd/issues/650
// Limit the number of node traversals to avoid out-of-memory
const val MAX_CHILD_SIZE = 512
const val MAX_DESCENDANTS_SIZE = 4096

private const val A11Y_NODE_TIME_KEY = "generatedTime"
fun AccessibilityNodeInfo.setGeneratedTime(): AccessibilityNodeInfo {
    extras.putLong(A11Y_NODE_TIME_KEY, System.currentTimeMillis())
    return this
}

fun AccessibilityNodeInfo.isExpired(expiryMillis: Long): Boolean {
    val generatedTime = extras.getLong(A11Y_NODE_TIME_KEY, -1)
    if (generatedTime == -1L) {
        // https://github.com/gkd-kit/gkd/issues/759
        return true
    }
    return (System.currentTimeMillis() - generatedTime) > expiryMillis
}

val selectorTypeModel by lazy { createDefaultSelectorTypeModel() }

val AccessibilityNodeInfo.compatChecked: Boolean?
    get() = if (AndroidTarget.BAKLAVA) {
        when (checked) {
            AccessibilityNodeInfo.CHECKED_STATE_TRUE -> true
            AccessibilityNodeInfo.CHECKED_STATE_FALSE -> false
            AccessibilityNodeInfo.CHECKED_STATE_PARTIAL -> null
            else -> null
        }
    } else {
        @Suppress("DEPRECATION")
        isChecked
    }


private const val interestedEvents = STATE_CHANGED or CONTENT_CHANGED
fun AccessibilityEvent?.isUseful(): Boolean {
    contract {
        returns(true) implies (this@isUseful != null)
    }
    return (this != null && packageName != null && className != null && eventType and interestedEvents != 0)
}

data class A11yEvent(
    val type: Int,
    val time: Long,
    val appId: String,
    val name: String,
    val event: AccessibilityEvent,
) {
    val safeSource: AccessibilityNodeInfo?
        get() = event.safeSource

    fun sameAs(other: A11yEvent): Boolean {
        if (other === this) return true
        return type == other.type && appId == other.appId && name == other.name
    }
}

// AccessibilityEvent's clear method will be called by some systems at a later time, causing internal data loss and leading to inconsistent data obtained by async child threads
fun AccessibilityEvent.toA11yEvent(): A11yEvent? {
    val appId = packageName ?: return null
    val b = className ?: return null
    return A11yEvent(
        type = eventType,
        time = System.currentTimeMillis(),
        appId = appId.toString(),
        name = b.toString(),
        event = this,
    )
}
