package li.gkd.app.data.appinfo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.core.content.ContextCompat
import li.gkd.app.app

object AppChangeMonitor {
    fun register(onChanged: (String) -> Unit) {
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    intent.data?.schemeSpecificPart?.let(onChanged)
                }
            },
            IntentFilter().apply {
                arrayOf(
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REPLACED,
                    Intent.ACTION_PACKAGE_REMOVED,
                ).forEach(::addAction)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        // 某些设备 ACTION_PACKAGE_ADDED 接收不到，使用 LauncherApps.Callback 作为补充。
        app.launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                onChanged(packageName)
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                onChanged(packageName)
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                onChanged(packageName)
            }

            override fun onPackagesAvailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) = Unit

            override fun onPackagesUnavailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) = Unit
        })
    }
}
