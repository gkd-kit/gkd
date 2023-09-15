package li.songe.gkd.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

fun shizukuIsSafeOK(): Boolean {
    return try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}