package li.songe.gkd.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val id: String,
    val name: String,
    val icon: Drawable?,
    val versionCode: Int,
    val versionName: String?,
    val isSystem: Boolean,
)