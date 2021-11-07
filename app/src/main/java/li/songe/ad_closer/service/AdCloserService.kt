package li.songe.ad_closer.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.*
import li.songe.ad_closer.data.Rule
import li.songe.ad_closer.util.MatchRule
import li.songe.ad_closer.util.findNodeInfo


/**
 * demo: https://juejin.cn/post/6844903589127651335
 */
class AdCloserService : AccessibilityService() {

    private fun getActivityInfo(componentName: ComponentName): ActivityInfo? {
        return try {
            packageManager.getActivityInfo(componentName, 0)
        } catch (e: NameNotFoundException) {
            null
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
//    override fun onDestroy() {
//        super.onDestroy()
//    }

    private var currentPackageName: String? = null
//    考虑n种模式
//    默认情况 正常获取 activity class name
//    不使用 activity class name, 直接用 package class name, 会导致 匹配规则 变多
//    借助 shizuku 使用 adb 获取 activity class name

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
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
                            val nodeInfo =
                                findNodeInfo(window, rule.matchUnit, listOf(0))
                            if (nodeInfo != null) {
                                LogUtils.dTag("click", rule.rawText)
                                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                nodeInfo.recycle()
                                return@loop
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
        if (event == null) {
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
//                LogUtils.d(rootInActiveWindow?.packageName, event.packageName, event.className)
//                if (event.packageName == rootInActiveWindow?.packageName && event.className != null && event.className.startsWith(
//                        event.packageName
//                    )
//                ) {
//
//                }
                val className = event.className.toString()
//                val packageName = event.packageName.toString()
//                在桌面和应用之间来回切换, 大概率导致识别失败
                if (!className.startsWith("android.") && !className.startsWith("androidx.")) {
//                    className.startsWith(packageName)
                    currentActivityClassName = className
                }
            }
            else -> {
            }
        }
//        val componentName =
//            ComponentName(
//                event.packageName?.toString() ?: "",
//                event.className?.toString() ?: ""
//            )
//        val activityInfo = getActivityInfo(componentName)
//        if (activityInfo != null) {
//            val newClassName = event.className.toString()
//            if (currentActivityClassName != newClassName) {
//                currentActivityClassName = newClassName
////                LogUtils.dTag("newClassName", newClassName, rootInActiveWindow?.packageName)
//            }
//        }
//        when (event.eventType) {
//            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
//            }
//            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
//            }
//            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
//
//            }
//            else -> {
//            }
//        }
    }

    private var currentActivityClassName = ""
        set(value) {
            if (field != value) {
                field = value
                LogUtils.dTag("updateClassName", field, rootInActiveWindow?.packageName)
            }
        }

    override fun onInterrupt() {
        scope.cancel()
    }


}