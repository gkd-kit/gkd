package li.songe.gkd.composition

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

open class CompositionAbService(
    private val block: CompositionAbService.() -> Unit,
) : AccessibilityService(),
    CanOnDestroy,
    CanOnStartCommand,
    CanOnAccessibilityEvent,
    CanOnServiceConnected,
    CanOnInterrupt {
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

    private val onAccessibilityEventHooks by lazy { linkedSetOf<(AccessibilityEvent?) -> Unit>() }
    override fun onAccessibilityEvent(f: (AccessibilityEvent?) -> Unit) =
        onAccessibilityEventHooks.add(f)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        onAccessibilityEventHooks.forEach { f -> f(event) }
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
}