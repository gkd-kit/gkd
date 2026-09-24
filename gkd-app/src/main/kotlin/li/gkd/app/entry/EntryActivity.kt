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
        intent?.let { sourceIntent ->
            val navIntent = Intent(sourceIntent)
            navIntent.component = MainActivity::class.componentName
            // Keep the existing MainActivity and its ViewModel, and handle entry parameters via onNewIntent.
            // Only forward URI authorization to avoid external task stack flags changing MainActivity's launch behavior.
            navIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or sourceIntent.uriPermissionFlags()
            navIntent.putExtra(activityNavSourceName, this::class.jvmName)
            startActivity(navIntent)
        }
        finish()
    }
}

private fun Intent.uriPermissionFlags() = flags and (
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    )
