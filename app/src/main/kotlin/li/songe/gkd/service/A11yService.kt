package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.a11y.A11yContext
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.a11y.a11yContext
import li.songe.gkd.a11y.isUseful
import li.songe.gkd.a11y.onA11yFeatInit
import li.songe.gkd.a11y.setGeneratedTime
import li.songe.gkd.a11y.typeInfo
import li.songe.gkd.data.ActionPerformer
import li.songe.gkd.data.ActionResult
import li.songe.gkd.data.GkdAction
import li.songe.gkd.data.RpcError
import li.songe.gkd.util.OnA11yLife
import li.songe.gkd.util.componentName
import li.songe.selector.MatchOption
import li.songe.selector.Selector

abstract class A11yService : AccessibilityService(), OnA11yLife {
    override fun onCreate() = onCreated()
    override fun onServiceConnected() = onA11yConnected()
    override fun onInterrupt() {}
    override fun onDestroy() = onDestroyed()
    override val a11yEventCbs = mutableListOf<(AccessibilityEvent) -> Unit>()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !event.isUseful) return
        onA11yEvent(event)
    }

    val safeActiveWindow: AccessibilityNodeInfo?
        get() = try {
            // 某些应用耗时 554ms
            // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
            rootInActiveWindow?.setGeneratedTime()
        } catch (_: Throwable) {
            null
        }.apply {
            a11yContext.rootCache = this
        }

    val safeActiveWindowAppId: String?
        get() = safeActiveWindow?.packageName?.toString()

    val scope = useScope()

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        onCreated { a11yRef = this }
        onDestroyed { a11yRef = null }
        A11yRuleEngine(this)
        onA11yFeatInit()
    }

    companion object {
        val a11yComponentName by lazy { SelectToSpeakService::class.componentName }
        val a11yClsName by lazy { a11yComponentName.flattenToShortString() }

        val isRunning = MutableStateFlow(false)
        private var a11yRef: A11yService? = null
        val instance: A11yService?
            get() = a11yRef

        fun execAction(gkdAction: GkdAction): ActionResult {
            val service = instance ?: throw RpcError("无障碍没有运行")
            val selector = Selector.parseOrNull(gkdAction.selector) ?: throw RpcError("非法选择器")
            runCatching { selector.checkType(typeInfo) }.exceptionOrNull()?.let {
                throw RpcError("选择器类型错误:${it.message}")
            }
            val matchOption = MatchOption(fastQuery = gkdAction.fastQuery)
            val targetNode = service.safeActiveWindow?.let {
                A11yContext(true).querySelfOrSelector(
                    it,
                    selector,
                    matchOption
                )
            } ?: throw RpcError("没有查询到节点")
            return ActionPerformer
                .getAction(gkdAction.action ?: ActionPerformer.None.action)
                .perform(targetNode, gkdAction.position)
        }
    }
}