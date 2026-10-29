package li.gkd.app.matching.input

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.AndroidAutomationSessions
import li.gkd.app.matching.MatchInvalidationReason
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.util.monotonicTimeMillis

@Volatile
var isInteractive = true
    private set
private val screenStateReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            LogUtils.i("screenStateReceiver->${action}")
            // Broadcast delivery can lag behind a newer sleep/wake transition.
            isInteractive = app.powerManager.isInteractive
            val decision =
                MatchingRuntime.inputs.accept(
                    MatchInput.Screen(isInteractive, action == Intent.ACTION_SCREEN_OFF),
                    MatchingRuntime.coordinator.policy.value,
                )
            if (decision.cancelWork) {
                MatchingRuntime.coordinator.invalidate(MatchInvalidationReason.ScreenOff)
            }
            if (isInteractive) {
                val t = monotonicTimeMillis()
                if (t - appChangeTime > 500) { // 37.872(a11y) -> 38.228(onReceive)
                    AndroidAutomationSessions.onScreenForcedActive()
                }
            }
        }
    }

fun initScreenEvents() {
    isInteractive = app.powerManager.isInteractive
    ContextCompat.registerReceiver(
        app,
        screenStateReceiver,
        IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        },
        ContextCompat.RECEIVER_EXPORTED,
    )
}

fun initMatchingRuleObservers() {
    appScope.launch {
        SubscriptionState.ruleSummaryFlow.collect { snapshot ->
            AndroidForegroundState.onRulesChanged(snapshot)
        }
    }
}
