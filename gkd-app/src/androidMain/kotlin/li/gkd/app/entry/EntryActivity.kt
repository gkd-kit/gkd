package li.gkd.app.entry

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.util.componentName

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
            // 保留已有 MainActivity 及其 ViewModel，由 onNewIntent 处理入口参数。
            // 只转发 URI 授权，避免外部任务栈 flags 改变 MainActivity 的启动行为。
            navIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or sourceIntent.uriPermissionFlags()
            navIntent.putExtra(activityNavSourceName, javaClass.name)
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
