package li.songe.gkd.util

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import java.net.NetworkInterface


object Ext {

    fun Bitmap.isEmptyBitmap(): Boolean {
        val emptyBitmap = Bitmap.createBitmap(width, height, config)
        return this.sameAs(emptyBitmap)
    }

    fun getIpAddressInLocalNetwork(): Sequence<String> {
        val networkInterfaces = try {
            // android.system.ErrnoException: getifaddrs failed: EACCES (Permission denied)
            NetworkInterface.getNetworkInterfaces().iterator().asSequence()
        } catch (e: Exception) {
            toast("获取host失败:" + e.message)
            emptySequence()
        }
        val localAddresses = networkInterfaces.flatMap {
            it.inetAddresses.asSequence().filter { inetAddress ->
                inetAddress.isSiteLocalAddress && !(inetAddress.hostAddress?.contains(":")
                    ?: false) && inetAddress.hostAddress != "127.0.0.1"
            }.map { inetAddress -> inetAddress.hostAddress }
        }
        return localAddresses
    }

    fun PackageManager.getDefaultLauncherAppId(): String? {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val defaultLauncher = this.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return defaultLauncher?.activityInfo?.packageName
    }

}