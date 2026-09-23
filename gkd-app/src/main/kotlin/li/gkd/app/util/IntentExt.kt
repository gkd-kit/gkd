package li.gkd.app.util

import li.gkd.app.text.UiStrings
import li.gkd.app.util.ToastUtils.toast

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import li.songe.codeorigin.CallSite

fun Context.tryStartActivity(
    intent: Intent,
    @CallSite loc: String = "",
) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        LogUtils.d("tryStartActivity", e, loc = loc)
        toast(UiStrings.intent_launch_failed_prefix + (e.message ?: e.stackTraceToString()), loc = loc)
    }
}

val Intent.extraCptName: ComponentName?
    get() = if (AndroidTarget.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_COMPONENT_NAME) as? ComponentName?
    }
