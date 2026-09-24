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
    private var registered = false

    @Synchronized
    fun register(onChanged: (String) -> Unit) {
        check(!registered) { "App change listener is already registered" }
        registered = true
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    intent?.data?.schemeSpecificPart?.let(onChanged)
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

        // Some devices cannot receive ACTION_PACKAGE_ADDED; use LauncherApps.Callback as a supplement.
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
