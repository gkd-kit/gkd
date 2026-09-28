package li.gkd.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import li.gkd.app.app
import li.gkd.app.appReady
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.util.LogUtils
import java.util.concurrent.TimeUnit

/** 每次自愈动作之间间隔 30 秒, 最多 [A11yBootHealScheduler.MAX_ATTEMPTS] 次 */
private const val HEAL_RETRY_INTERVAL_SECONDS = 30L

private const val HEAL_WORK_NAME = "gkd-a11y-boot-heal"

private const val HEAL_ATTEMPT_PREFS = "gkd_a11y_heal"

private const val HEAL_ATTEMPT_KEY = "attempt"

private const val HEAL_SKIP_KEY = "skip"

/**
 * 开机后触发无障碍"假在线"自愈。
 *
 * 故意不在此处启动 [A11yService]: Android 12+ 禁止后台直接启动前台服务,
 * 而重写 enabled_accessibility_services 已足以让系统自行重新绑定无障碍服务, 无需拉起进程。
 */
class A11yBootHealReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        LogUtils.d("A11yBootHealReceiver onReceive", action)
        A11yBootHealScheduler.resetCounters()
        A11yBootHealScheduler.enqueue()
    }
}

/**
 * 自愈重试调度。
 *
 * 用周期任务而不是一次性任务加退避: 周期任务由 WorkManager 去重, 反复入队不会互相取消,
 * 且每次动作只重写一条 settings 记录, 不需要进程常驻, 因此不会引入额外唤醒与耗电。
 * 到达重试上限或自愈成功后由 [A11yHealWorker] 主动取消。
 */
object A11yBootHealScheduler {
    const val MAX_ATTEMPTS = 5

    /**
     * 权限服务最多可以晚到几轮。
     *
     * 实测该设备上 Shizuku 处理 BOOT_COMPLETED 比本应用晚约 12 秒, 因此首轮通常还在等待权限,
     * 预留 3 轮(约 90 秒)等待; 与自愈尝试次数分开计数, 避免权限就绪前就耗尽重试次数。
     */
    const val MAX_SKIPS = 3

    fun enqueue() {
        val request: PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<A11yHealWorker>(HEAL_RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .setInitialDelay(HEAL_RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .build()
        try {
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                HEAL_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } catch (e: Throwable) {
            LogUtils.d("A11yBootHealScheduler enqueue failed", e)
        }
    }

    fun cancel() {
        try {
            WorkManager.getInstance(app).cancelUniqueWork(HEAL_WORK_NAME)
        } catch (e: Throwable) {
            LogUtils.d("A11yBootHealScheduler cancel failed", e)
        }
    }

    fun transientPrefs() = app.getSharedPreferences(HEAL_ATTEMPT_PREFS, Context.MODE_PRIVATE)

    /** 等待权限服务就绪的轮数, 与自愈尝试次数分开计数 */
    fun skipCount(): Int = transientPrefs().getInt(HEAL_SKIP_KEY, 0)

    fun markSkip() {
        val prefs = transientPrefs()
        prefs.edit().putInt(HEAL_SKIP_KEY, prefs.getInt(HEAL_SKIP_KEY, 0) + 1).apply()
    }

    fun attemptCount(): Int = transientPrefs().getInt(HEAL_ATTEMPT_KEY, 0)

    fun markAttempt(): Int {
        val prefs = transientPrefs()
        val attempt = prefs.getInt(HEAL_ATTEMPT_KEY, 0) + 1
        prefs.edit().putInt(HEAL_ATTEMPT_KEY, attempt).apply()
        return attempt
    }

    fun resetCounters() = transientPrefs().edit().clear().apply()
}

/**
 * 单次自愈尝试: 检测假在线状态, 重写 enabled_accessibility_services 并等待系统重新绑定。
 *
 * 达到重试上限仍未恢复时取消周期任务并发通知提示用户手动恢复。
 */
class A11yHealWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!storeFlow.value.enableBootHeal) {
            A11yBootHealScheduler.resetCounters()
            A11yBootHealScheduler.cancel()
            return Result.success()
        }
        if (!appReady.value) return Result.success()
        // 权限服务(Shizuku/root)尚未就绪时不计入自愈次数, 只受 MAX_SKIPS 限制
        if (!A11yHealService.canHealNow()) {
            if (A11yBootHealScheduler.skipCount() >= A11yBootHealScheduler.MAX_SKIPS) {
                LogUtils.d("A11yHealWorker give up: privilege service never ready")
                A11yBootHealScheduler.resetCounters()
                A11yBootHealScheduler.cancel()
                A11yHealService.notifyHealFailed()
                return Result.failure()
            }
            A11yBootHealScheduler.markSkip()
            LogUtils.d("A11yHealWorker skip: privilege service not ready")
            return Result.success()
        }
        val attempt = A11yBootHealScheduler.markAttempt()
        return try {
            val result = A11yHealService.healNow("boot#$attempt")
            LogUtils.d("A11yHealWorker doWork", attempt, result)
            when (result) {
                A11yHealResult.Healed, A11yHealResult.Healthy -> {
                    A11yBootHealScheduler.resetCounters()
                    A11yBootHealScheduler.cancel()
                    Result.success()
                }

                A11yHealResult.Retry, A11yHealResult.Unavailable -> finishOrRetry(attempt)
            }
        } catch (e: Throwable) {
            LogUtils.d("A11yHealWorker failed", e)
            finishOrRetry(attempt)
        }
    }

    private fun finishOrRetry(attempt: Int): Result {
        if (attempt < A11yBootHealScheduler.MAX_ATTEMPTS) return Result.success()
        A11yBootHealScheduler.resetCounters()
        A11yBootHealScheduler.cancel()
        A11yHealService.notifyHealFailed()
        return Result.failure()
    }
}
