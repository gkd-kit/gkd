package li.songe.gkd.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ServiceUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import li.songe.gkd.data.RuleMap
import li.songe.gkd.data.Subscription
import li.songe.gkd.server.api.NodeData
import li.songe.gkd.shizuku.Shell
import li.songe.gkd.shizuku.ShizukuShell
import li.songe.gkd.store.Storage
import li.songe.selector.GkdSelector


/**
 * demo: https://juejin.cn/post/6844903589127651335
 */
class GkdAccessService : AccessibilityService() {
    companion object {
        fun isRunning() = ServiceUtils.isServiceRunning(GkdAccessService::class.java)
        fun rootInActiveWindow() = service?.rootInActiveWindow
        fun currentActivityName() = service?.targetClassName
        fun currentPackageName() = service?.rootInActiveWindow?.packageName?.toString()
        private var service: GkdAccessService? = null
        private const val notificationId = 0
    }

    private val scope by lazy { CoroutineScope(Dispatchers.Default) }

    private var runMode: UInt = 0b10u
//    0b01u : shizuku
//    0b10u : AccessibilityService

    override fun onCreate() {
        super.onCreate()
        LogUtils.d("启动无障碍服务")
        service = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }


    private val logMap = mutableMapOf<GkdSelector, Long>()
    private var collectCount = 0
    private var collectCostTime = 0L

//    private suspend fun delay2(timeMillis: Long) {
//        if (timeMillis <= 0) return
//        suspendCoroutine<Unit> {
//            thread {
//                Thread.sleep(timeMillis)
//                if (it.context.isActive) {
//                    LogUtils.d(timeMillis)
//                    it.resume(Unit)
//                }
//            }
//        }
//    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        LogUtils.d("onServiceConnected")
//        thread {
//            while (true) {
//                val t = System.currentTimeMillis()
//                Thread.sleep(5000)
//                LogUtils.d("work:${System.currentTimeMillis() - t}")
//            }
//        }

        scope.launch {

            var lastPackageName = rootInActiveWindow?.packageName?.toString()
            var clsName = targetClassName
//            var i = 0L
//            val sl = mutableListOf<Long>()
            while (isActive) {
                delay(200)

//                val s = System.currentTimeMillis()
                if (!Storage.settings.enableService) {
                    continue
                }
                if (ScreenUtils.isScreenLock()) {
                    continue
                }
                val window: AccessibilityNodeInfo = rootInActiveWindow ?: continue
                val targetPackageName = window.packageName?.toString() ?: continue
                if (lastPackageName != targetPackageName || clsName != targetClassName) {
                    lastPackageName = targetPackageName
                    clsName = targetClassName.toString()
                    LogUtils.d(
                        "$lastPackageName/$clsName",
                        gkdRuleMap.count(lastPackageName, clsName),
                        runMode.toString(2)
                    )
                }
//                if (System.currentTimeMillis() - i > 15000) {
//                    i = System.currentTimeMillis()
//                    LogUtils.d(windows?.map { it?.root?.packageName }
//                        ?.joinToString { it ?: "null" }, sl)
//                }

                try {
                    targetClassName?.let {
                        val c = it
                        gkdRuleMap.traverse(targetPackageName, it) { gkdSelector ->
                            if (logMap.contains(gkdSelector) && System.currentTimeMillis() - logMap[gkdSelector]!! < 1000) {
                                return@traverse Unit
                            }
                            if (targetPackageName != rootInActiveWindow?.packageName?.toString() || c != targetClassName) {
                                return@traverse Unit
                            }
                            logMap.remove(gkdSelector)
                            val startTime = System.currentTimeMillis()
                            val targetNodeInfo = gkdSelector.collect(window)
                            collectCostTime += System.currentTimeMillis() - startTime
                            collectCount++
                            if (collectCount % 100 == 0) {
                                LogUtils.d("count:$collectCount, time:$collectCostTime, average:${collectCostTime / collectCount}")
                            }
                            if (targetNodeInfo == null) return@traverse Unit
                            val clickResult =
                                gkdSelector.click(targetNodeInfo, this@GkdAccessService)
                            LogUtils.d(
                                gkdSelector.toString(),
                                NodeData.info2data(targetNodeInfo),
                                clickResult
                            )
                            logMap[gkdSelector] = System.currentTimeMillis()
                            delay(10)
                            return@traverse true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
//                if (System.currentTimeMillis() - s > 2000) {
//                    sl.add(System.currentTimeMillis() - s)
//                }
            }
        }

//        scope.launch {
//            var count = 0
//            while (true) {
//                count++
//                val startTime = System.currentTimeMillis()
//                delay2(5000L)
//                LogUtils.d(withTimeoutOrNull(250) {
//                    withContext(IO) {
//                        try {
//                            ShizukuShell.instance.exec(Shell.Command("dumpsys activity activities | grep mResumedActivity")).out
//                        } catch (e: Exception) {
//                            e
//                        }
//                    }
//                }, count, System.currentTimeMillis() - startTime)
//            }
//        }


        scope.launch {
//            var i = 0L
//            var lastPackName = ""
            val delayTime = 300L
            while (isActive) {
                delay(delayTime)
                if (!Storage.settings.enableService) {
                    continue
                }
                if (ScreenUtils.isScreenLock()) {
                    continue
                }
                try {
                    ShizukuShell.instance.apply {
                        if (isAvailable) {
                            val result = withTimeout(250) {
                                withContext(IO) {
                                    exec(Shell.Command("dumpsys activity activities | grep mResumedActivity"))
                                }
                            }
//                            if (System.currentTimeMillis() - i > 10000) {
//                                i = System.currentTimeMillis()
//                                LogUtils.d(result)
//                            }
                            val strList = result.out.split("\u0020")
                            if (!result.isSuccessful || strList.size < 4 || !strList[3].contains('/')) {
                                LogUtils.d(result)
                                return@apply
                            }
                            var (packageName, activityName) = strList[3].split(
                                '/'
                            )
                            if (activityName.startsWith('.')) {
                                activityName = packageName + activityName
                            }
                            runMode = runMode or 0b01u
                            targetClassName = activityName
//                            Log.d("shizuku", activityName)
//                            count++
                        } else {
                            runMode = runMode and 0b10u
                        }
                    }
                } catch (e: Exception) {
                    runMode = runMode and 0b10u
                    e.printStackTrace()
                }
            }
        }
    }


    private val gkdRuleMap by lazy {
        RuleMap().apply {
            val source = assets.open("ad-internal.gkd.json").readBytes().toString(Charsets.UTF_8)
            load(Subscription.parse(source))
        }
    }

    private suspend fun delay2(timeMillis: Long) {
        withTimeoutOrNull(timeMillis) {
            delay(timeMillis)
        }
    }

    //    var lastChangeTime = -1L
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.className == null) {
            return
        }
        if (runMode and 0b01u == 0b01u) {
//            shizuku is work
            return
        }
//        if (System.currentTimeMillis() - lastChangeTime < 500) {
//            return
//        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
//                lastChangeTime = System.currentTimeMillis()
                val className = event.className.toString()
//                val packageName = event.packageName.toString()
//                在桌面和应用之间来回切换, 大概率导致识别失败
                if (!className.startsWith("android.") &&
                    !className.startsWith("androidx.") &&
                    !className.startsWith("com.android.")
                ) {
//                    className.startsWith(packageName)
                    val rootPackageName = rootInActiveWindow?.packageName?.toString() ?: ""
                    if ((className == "com.miui.home.launcher.Launcher" && rootPackageName != "com.miui.home")) {
//                        小米手机 上滑手势, 导致 活动名 不属于包名
//                        另外 微信扫码登录第三方网站 也会导致失败
                    } else {
                        targetClassName = className
                    }
                }
            }
            else -> {
            }
        }
    }

    private var targetClassName: String? = null
        set(value) {
            if (field != value) {
                field = value
                createNotificationChannel(value ?: "未知")
//                var count = 0
//                val p = rootInActiveWindow?.packageName?.toString() ?: ""
//                targetClassName?.let {
//                    gkdRuleMap.traverse(
//                        p,
//                        it
//                    ) {
//                        count++
//                    }
//                }
//                LogUtils.dTag(
//                    "updateClassName",
//                    "$p/$value",
//                    gkdRuleMap.count(p, value ?: ""),
//                    runMode.toString(2)
//                )
//
            }
        }

    override fun onInterrupt() {
        LogUtils.d("onInterrupt")

    }

    override fun onDestroy() {
        super.onDestroy()
        service = null
//        notificationManager.cancel(notificationId)
        scope.cancel()
        LogUtils.d("关闭无障碍服务")
    }


    private fun createNotificationChannel(cls: String = "未知") {

        KeepAliveService.start(this)
//
//        val channelId = "gkd_service"
//        val intent = Intent(this, MainActivity::class.java).apply {
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//        }
//
//        val pendingIntent: PendingIntent = PendingIntent.getActivity(
//            this,
//            0,
//            intent,
//            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//        )
//
//        val builder = NotificationCompat.Builder(this, channelId)
//            .setSmallIcon(R.drawable.ic_app_2)
//            .setContentTitle("无障碍已授权")
//            .setContentText(cls)
//            .setContentIntent(pendingIntent)
//            .setOngoing(true)
//
//
//        val name = "无障碍服务"
//        val descriptionText = "无障碍服务,基于规则模拟点击"
//        val importance = NotificationManager.IMPORTANCE_HIGH
//        val channel = NotificationChannel(channelId, name, importance).apply {
//            description = descriptionText
//        }
//        notificationManager.createNotificationChannel(channel)
//        val notification = builder.build()
//        notificationManager.notify(notificationId, notification)
    }

//    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
}
