package li.gkd.app.entry

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.util.componentName
import kotlin.reflect.jvm.jvmName

abstract class EntryActivity : Activity() {
    companion object {
        val activityNavSourceName by lazy { META.appId + ".activity.nav.source" }
    }

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareIntent()
        navToMainActivity()
    }

    protected open fun prepareIntent() {}

    private fun navToMainActivity() {
        if (intent != null) {
            val navIntent = Intent(intent)
            navIntent.component = MainActivity::class.componentName
            navIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            navIntent.putExtra(activityNavSourceName, this::class.jvmName)
            startActivity(navIntent)
        }
        finish()
    }
}
