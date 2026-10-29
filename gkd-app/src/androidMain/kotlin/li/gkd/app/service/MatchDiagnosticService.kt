package li.gkd.app.service

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.lifecycle.lifecycleScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import li.gkd.app.app
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchDiagnosticFiles
import li.gkd.app.matching.diagnostics.MatchDiagnosticState
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.notif.StopServiceReceiver
import li.gkd.app.permission.PermissionStates
import li.gkd.app.resources.*
import li.gkd.app.util.ToastUtils

/** Owns capture, not matching work. Closing a page never stops this service. */
class MatchDiagnosticService : LifecycleHookService() {
    private var sessionId: String? = null
    private var starting = false
    private var stoppedFromNotification = false

    init {
        val receiver =
            StopServiceReceiver(this) {
                stoppedFromNotification = true
                sessionId?.let { MatchingRuntime.coordinator.stopDiagnostics(it) } ?: stopSelf()
            }
        onCreated { receiver.register() }
        onDestroyed { receiver.close() }
        onDestroyed { sessionId?.let { MatchingRuntime.coordinator.stopDiagnostics(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        @Suppress("DEPRECATION") val reply = intent?.getParcelableExtra<ResultReceiver>("reply")
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (starting || sessionId != null) {
            reply?.send(
                1,
                Bundle().apply {
                    putString("error", Res.string.match_debug_service_busy.getSync())
                },
            )
            return START_NOT_STICKY
        }
        if (!NotificationCatalog.matchDiagnostic().startForeground()) {
            reply?.send(
                1,
                Bundle().apply {
                    putString("error", Res.string.match_debug_service_failed.getSync())
                },
            )
            return START_NOT_STICKY
        }
        starting = true
        lifecycleScope.launch {
            val coordinator = MatchingRuntime.coordinator
            try {
                val session =
                    coordinator.startDiagnostics(
                        intent.getBooleanExtra("detailed", true),
                        intent.getStringExtra("rule"),
                        intent.getBooleanExtra("queries", true),
                        intent.getBooleanExtra("system", true),
                    )
                sessionId = session.id
                reply?.send(0, Bundle().apply { putString("id", session.id) })
                coordinator.diagnostics.state.first { it?.id != session.id || !it.active }
                NotificationCatalog.matchDiagnostic(saving = true).startForeground()
                MatchDiagnosticFiles.state.first { it.containsKey(session.id) }
                MatchDiagnosticFiles.flush()
                if (stoppedFromNotification) {
                    NotificationCatalog.matchDiagnosticSaved().post()
                } else {
                    ToastUtils.show(
                        org.jetbrains.compose.resources.getString(Res.string.match_debug_saved),
                    )
                }
            } catch (error: Exception) {
                if (sessionId == null)
                    reply?.send(
                        if (error is IllegalArgumentException) 2 else 1,
                        Bundle().apply { putString("error", error.message ?: error.toString()) },
                    )
                if (error is CancellationException) throw error
                LogUtils.e(error)
            } finally {
                starting = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        suspend fun start(
            detailed: Boolean,
            ruleFilter: String?,
            recordQueries: Boolean,
            recordSystem: Boolean,
        ): MatchDiagnosticState {
            check(PermissionStates.notification.updateAndGet()) {
                Res.string.match_debug_notification_required.getSync()
            }
            return suspendCancellableCoroutine { continuation ->
                val reply =
                    object : ResultReceiver(Handler(Looper.getMainLooper())) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            if (!continuation.isActive) return
                            if (resultCode == 0)
                                continuation.resume(
                                    MatchingRuntime.coordinator.diagnostics.session(
                                        checkNotNull(resultData?.getString("id")),
                                    ),
                                )
                            else
                                continuation.resumeWithException(
                                    if (resultCode == 2)
                                        IllegalArgumentException(resultData?.getString("error"))
                                    else IllegalStateException(resultData?.getString("error")),
                                )
                        }
                    }
                val intent =
                    Intent(app, MatchDiagnosticService::class.java).apply {
                        putExtra("reply", reply)
                        putExtra("detailed", detailed)
                        putExtra("rule", ruleFilter)
                        putExtra("queries", recordQueries)
                        putExtra("system", recordSystem)
                    }
                try {
                    app.startForegroundService(intent)
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }
}
