package li.songe.gkd.notif

import li.songe.gkd.app
import li.songe.gkd.util.SafeR

data class Notif(
    val id: Int,
    val smallIcon: Int = SafeR.ic_status,
    val title: String = app.getString(SafeR.app_name),
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