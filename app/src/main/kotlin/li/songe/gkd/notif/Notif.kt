package li.songe.gkd.notif

import li.songe.gkd.R
import li.songe.gkd.util.APP_NAME

data class Notif(
    val id: Int,
    @Suppress("UNRESOLVED_REFERENCE")
    val smallIcon: Int = R.drawable.ic_status,
    val title: String = APP_NAME,
    val text: String,
    val ongoing: Boolean,
    val autoCancel: Boolean,
)

val abNotif by lazy {
    Notif(
        id = 100,
        text = "无障碍正在运行",
        ongoing = true,
        autoCancel = false,
    )
}

val screenshotNotif by lazy {
    Notif(
        id = 101,
        text = "截屏服务正在运行",
        ongoing = true,
        autoCancel = false,
    )
}

val floatingNotif by lazy {
    Notif(
        id = 102,
        text = "悬浮窗按钮正在显示",
        ongoing = true,
        autoCancel = false,
    )
}

val httpNotif by lazy {
    Notif(
        id = 103,
        text = "HTTP服务正在运行",
        ongoing = true,
        autoCancel = false,
    )
}