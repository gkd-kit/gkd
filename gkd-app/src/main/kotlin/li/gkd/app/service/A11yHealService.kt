package li.gkd.app.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.os.UserManager
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.util.LogUtils
import li.gkd.app.util.launchLogged

/**
 * 无障碍"假在线"的识别与自愈。
 *
 * 系统设置中的 enabled 记录与服务实际绑定状态并不一致: 开机阶段绑定失败或超时后,
 * Settings.Secure 中的条目会被保留, 但 AccessibilityManagerService 不会再重试,
 * 服务会长期停留在"设置里已开启但进程从未被拉起"的状态, 只能靠用户手动开关一次恢复。
 *
 * 唯一能强制系统重读配置的手段是重写 enabled_accessibility_services,
 * 因此本模块不依赖服务自身存活, 可以被开机广播单独唤起。
 */
sealed interface A11yHealResult {
    data object Healthy : A11yHealResult
    data object Healed : A11yHealResult
    data object Retry : A11yHealResult
    data object Unavailable : A11yHealResult
}

object A11yHealService {
    /** 系统回读 enabled_accessibility_services 存在延迟, 过短的间隔会概率不触发重启 */
    const val A11Y_REBIND_INTERVAL_MILLIS = 1500L

    /** 单次重启后等待系统回调 onServiceConnected 的上限 */
    private const val CONNECT_WAIT_MILLIS = 4000L
    private const val CONNECT_WAIT_STEP_MILLIS = 250L

    /** 用户切换应用时会短暂关闭服务, 这段时间内的巡检不应触发自愈 */
    private const val PATROL_GRACE_MILLIS = 60_000L
    private const val PATROL_INTERVAL_MILLIS = 60_000L

    private val requests = Channel<String>(Channel.CONFLATED)
    private var consumerStarted = false
    private var patrolStarted = false

    fun stateSnapshot(): A11yHealState = detectState(
        connected = A11yService.connected.value,
        enabledInSettings = app.getSecureA11yServices().contains(A11yService.a11yCn),
        a11yModeSelected = storeFlow.value.useA11y,
        canWriteSecureSettings = PermissionStates.writeSecureSettings.updateAndGet(),
        userUnlocked = app.getSystemService(UserManager::class.java)?.isUserUnlocked != false,
        activeServicesMissing = isA11yServiceMissingFromActiveList(
            activeServices = runCatching {
                app.a11yManager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
                )
            }.getOrDefault(emptyList()),
            a11yCn = A11yService.a11yCn,
        ),
    )

    /** 系统主动解绑: 立即尝试一次自愈 */
    fun onA11yUnbound() = requestHeal("a11yUnbound")

    /**
     * 权限服务是否已经就绪到可以写入安全设置。
     *
     * 开机阶段 Shizuku 处理 BOOT_COMPLETED 的时间可能明显晚于本应用(实测约晚 12 秒),
     * 此时 [PermissionStates.writeSecureSettings] 尚未被授予权限, 属于"还没到能自愈的时机",
     * 必须与"自愈尝试失败"分开计数, 否则会在权限就绪前就耗尽重试次数。
     */
    fun canHealNow(): Boolean = privilegeContextFlow.value != null ||
            PermissionStates.writeSecureSettings.updateAndGet()

    /** 启动运行期巡检, 用于发现"设置已启用但服务未连接"的漂移 */
    fun startPatrol() {
        if (patrolStarted) return
        patrolStarted = true
        appScope.launchLogged {
            var lastConnectedTime = System.currentTimeMillis()
            while (true) {
                delay(PATROL_INTERVAL_MILLIS)
                if (A11yService.connected.value) {
                    lastConnectedTime = System.currentTimeMillis()
                    continue
                }
                if (!storeFlow.value.enableBootHeal) continue
                if (System.currentTimeMillis() - lastConnectedTime < PATROL_GRACE_MILLIS) continue
                lastConnectedTime = System.currentTimeMillis()
                if (stateSnapshot() == A11yHealState.FakeOnline) {
                    healNow("patrol")
                }
            }
        }
    }

    /** 立即尝试自愈一次, 未恢复时返回 [A11yHealResult.Retry] 交给调度器安排下一次重试 */
    suspend fun healNow(reason: String): A11yHealResult {
        val state = stateSnapshot()
        LogUtils.d("A11yHealService healNow", reason, state)
        return when (state) {
            A11yHealState.Healthy -> A11yHealResult.Healthy
            A11yHealState.Disabled -> A11yHealResult.Retry
            A11yHealState.Unavailable -> A11yHealResult.Retry
            A11yHealState.FakeOnline -> if (rebindA11yService() && awaitConnected()) {
                LogUtils.d("A11yHealService healed", reason)
                A11yHealResult.Healed
            } else {
                LogUtils.d("A11yHealService heal failed", reason)
                A11yHealResult.Retry
            }
        }
    }

    /**
     * 重写 enabled_accessibility_services 触发系统重新绑定。
     *
     * 先移除自己的条目并等待系统回读, 再连同其它应用的服务条目一起写回,
     * 既触发系统重启无障碍, 也不会影响系统中其它无障碍应用。
     */
    suspend fun rebindA11yService(): Boolean {
        if (!PermissionStates.writeSecureSettings.updateAndGet()) {
            LogUtils.d("A11yHealService rebind skipped: no WRITE_SECURE_SETTINGS")
            return false
        }
        return try {
            val a11yCn = A11yService.a11yCn
            val services = app.getSecureA11yServices()
            services.remove(a11yCn)
            app.putSecureA11yServices(services)
            // 必须等待一段时间, 否则概率不会触发系统重启无障碍
            delay(A11Y_REBIND_INTERVAL_MILLIS)
            // 等待期间用户可能已切换到自动化模式, 此时不应再写回无障碍条目
            if (!storeFlow.value.useA11y) return false
            app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            services.add(a11yCn)
            app.putSecureA11yServices(services)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LogUtils.d("A11yHealService rebind failed", e)
            false
        }
    }

    /** 全部自愈手段失败后的兜底通知, 引导用户手动恢复 */
    fun notifyHealFailed() {
        if (!PermissionStates.notification.updateAndGet()) return
        runCatching {
            NotificationCatalog.a11yRecoveryFailed().post()
        }.onFailure {
            LogUtils.d("A11yHealService notify failed", it)
        }
    }

    /** 有界等待服务连接, 避免自愈协程长期占用 */
    private suspend fun awaitConnected(): Boolean {
        var waited = 0L
        while (waited < CONNECT_WAIT_MILLIS) {
            if (A11yService.connected.value) return true
            delay(CONNECT_WAIT_STEP_MILLIS)
            waited += CONNECT_WAIT_STEP_MILLIS
        }
        return A11yService.connected.value
    }

    private fun requestHeal(reason: String) {
        check(requests.trySend(reason).isSuccess) { "无障碍自愈队列已关闭" }
        if (consumerStarted) return
        consumerStarted = true
        appScope.launch(Dispatchers.Default) {
            for (currentReason in requests) {
                try {
                    healNow(currentReason)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    LogUtils.d("A11yHealService requestHeal failed", e)
                }
            }
        }
    }
}

/**
 * 辅助判定: 系统当前真实生效的无障碍服务列表中是否缺少本应用的服务。
 *
 * Android 只允许具备 canRetrieveWindowContent 的调用方获取该列表, 无权限时返回空集合,
 * 因此空结果不能作为"服务缺失"的证据, 只有在结果非空时才能确认确实缺失。
 */
fun isA11yServiceMissingFromActiveList(
    activeServices: List<AccessibilityServiceInfo>,
    a11yCn: ComponentName,
): Boolean {
    if (activeServices.isEmpty()) return false
    return activeServices.none { serviceInfo ->
        val info = serviceInfo.resolveInfo?.serviceInfo ?: return@none false
        ComponentName(info.packageName, info.name) == a11yCn
    }
}
