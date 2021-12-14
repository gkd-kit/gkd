package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.*
import li.songe.gkd.data.Rule
import li.songe.gkd.util.MatchRule
import li.songe.gkd.util.findNodeInfo


/**
 * demo: https://juejin.cn/post/6844903589127651335
 */
class GkdService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
//    override fun onDestroy() {
//        super.onDestroy()
//    }

    override fun onCreate() {
        super.onCreate()
        LogUtils.d("onCreate")
    }

    private var job: Job? = null
    override fun onServiceConnected() {
        super.onServiceConnected()
        LogUtils.d("onServiceConnected")
        job = scope.launch {
            while (true) {
                if (!this.isActive) {
                    break
                }
                val window = rootInActiveWindow

//                val packageName = window?.packageName?.toString()
//                if (packageName != null && currentPackageName != packageName) {
//                    currentPackageName = packageName
//                    LogUtils.d(currentPackageName, currentActivityClassName)
//                }
                if (window != null && ruleListMap.containsKey(currentActivityClassName)) {
                    run loop@{
                        ruleListMap[currentActivityClassName]!!.forEachIndexed { _, rule ->
                            val nodeInfo = findNodeInfo(window, rule.matchUnit, listOf(0))
                            if (nodeInfo != null) {
                                LogUtils.dTag("findNode", nodeInfo)
                                var parentNodeInfo = nodeInfo
                                var level = 0
                                while (parentNodeInfo != null && !parentNodeInfo.isClickable) {
                                    parentNodeInfo = parentNodeInfo.parent
                                    level += 1
                                }
                                if (parentNodeInfo != null) {
                                    parentNodeInfo.apply {
                                        LogUtils.dTag("click", level, rule.rawText)
                                        performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    }
                                    return@loop
                                }
                            }

                        }
                    }
                }
                delay(200)
            }
        }
    }


    private val ruleListMap by lazy {
        val h = HashMap<String, MutableList<MatchRule>>()
        Rule.defaultRuleList.forEach {
            val key = it.className
            if (!h.containsKey(key)) {
                h[key] = mutableListOf()
            }
            try {
                h[key]?.add(MatchRule.parse(it.selector))
            } catch (e: Exception) {
                LogUtils.d(e)
            }
        }
        return@lazy h
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.className == null) {
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className.toString()
//                val packageName = event.packageName.toString()
//                在桌面和应用之间来回切换, 大概率导致识别失败
                if (!className.startsWith("android.") && !className.startsWith("androidx.")) {
//                    className.startsWith(packageName)
                    val rootPackageName = rootInActiveWindow?.packageName?.toString() ?: ""
                    if ((className == "com.miui.home.launcher.Launcher" && rootPackageName != "com.miui.home")) {
//                        上滑手势, 导致 活动名 不属于包名
                    } else {
                        currentActivityClassName = className
                    }
                }
            }
            else -> {
            }
        }
    }

    private var currentActivityClassName = ""
        set(value) {
            if (field != value) {
                field = value
                val packageName = rootInActiveWindow?.packageName
                LogUtils.dTag(
                    "updateClassName",
                    field,
                    packageName,
                )
            }
        }

    override fun onInterrupt() {
        job?.cancel()
        LogUtils.d("onInterrupt")

    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        LogUtils.d("onDestroy")
    }

}
