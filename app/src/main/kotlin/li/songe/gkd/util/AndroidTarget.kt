package li.songe.gkd.util

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object AndroidTarget {
    /** Android 9+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    val P = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Android 10+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    val Q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Android 11+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val R = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Android 12+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val S = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Android 13+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val TIRAMISU = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Android 14+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    val UPSIDE_DOWN_CAKE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Android 16+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
    val BAKLAVA = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
}