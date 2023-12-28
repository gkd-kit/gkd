package li.songe.gkd.composition

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

open class CompositionAbService(
    private val block: CompositionAbService.() -> Unit,
) : AccessibilityService(), CanOnDestroy, CanOnStartCommand, CanOnAccessibilityEvent,
    CanOnServiceConnected, CanOnInterrupt, CanOnKeyEvent {
    override fun onCreate() {
        super.onCreate()
        block(this)
    }

    private val onStartCommandHooks by lazy { linkedSetOf<StartCommandHook>() }
    override fun onStartCommand(f: StartCommandHook) = onStartCommandHooks.add(f)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onStartCommandHooks.forEach { f -> f(intent, flags, startId) }
        return super.onStartCommand(intent, flags, startId)
    }

    private val destroyHooks by lazy { linkedSetOf<() -> Unit>() }
    override fun onDestroy(f: () -> Unit) = destroyHooks.add(f)
    override fun onDestroy() {
        super.onDestroy()
        destroyHooks.forEach { f -> f() }
    }

    private val onAccessibilityEventHooks by lazy { linkedSetOf<(AccessibilityEvent) -> Unit>() }
    private val interestedEvents =
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

    override fun onAccessibilityEvent(f: (AccessibilityEvent) -> Unit) =
        onAccessibilityEventHooks.add(f)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null &&
            event.packageName != null &&
            event.className != null &&
            event.eventType.and(interestedEvents) != 0
        ) {
            onAccessibilityEventHooks.forEach { f -> f(event) }
        }
    }


    private val onInterruptHooks by lazy { linkedSetOf<() -> Unit>() }
    override fun onInterrupt(f: () -> Unit) = onInterruptHooks.add(f)
    override fun onInterrupt() {
        onInterruptHooks.forEach { f -> f() }
    }

    private val onServiceConnectedHooks by lazy { linkedSetOf<() -> Unit>() }
    override fun onServiceConnected(f: () -> Unit) = onServiceConnectedHooks.add(f)
    override fun onServiceConnected() {
        super.onServiceConnected()
        onServiceConnectedHooks.forEach { f -> f() }
    }


    private val onKeyEventHooks by lazy { linkedSetOf<(KeyEvent?) -> Unit>() }
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        onKeyEventHooks.forEach { f -> f(event) }
        return super.onKeyEvent(event)
    }

    override fun onKeyEvent(f: (KeyEvent?) -> Unit) {
        onKeyEventHooks.add(f)
    }
}