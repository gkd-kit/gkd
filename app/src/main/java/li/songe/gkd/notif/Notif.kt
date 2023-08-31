package li.songe.gkd.notif

import li.songe.gkd.util.SafeR

data class Notif(
    val id: Int,
    val icon: Int,
    val title: String,
    val text: String,
    val ongoing: Boolean,
    val autoCancel: Boolean,
)


val abNotif by lazy {
    Notif(
        id = 100,
        icon = SafeR.ic_launcher,
        title = "搞快点",
        text = "无障碍正在运行",
        ongoing = true,
        autoCancel = false
    )
}
val screenshotNotif by lazy {
    Notif(
        id = 101,
        icon = SafeR.ic_launcher,
        title = "搞快点",
        text = "截屏服务正在运行",
        ongoing = true,
        autoCancel = false
    )
}

val floatingNotif by lazy {
    Notif(
        id = 102,
        icon = SafeR.ic_launcher,
        title = "搞快点",
        text = "悬浮窗按钮正在显示",
        ongoing = true,
        autoCancel = false
    )
}