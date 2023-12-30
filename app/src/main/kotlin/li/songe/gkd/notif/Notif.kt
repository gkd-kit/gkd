package li.songe.gkd.notif

import li.songe.gkd.R

data class Notif(
    val id: Int,
    val icon: Int = R.drawable.ic_launcher,
    val title: String,
    val text: String,
    val ongoing: Boolean,
    val autoCancel: Boolean,
)


val abNotif by lazy {
    Notif(
        id = 100,
        title = "GKD",
        text = "无障碍正在运行",
        ongoing = true,
        autoCancel = false,
    )
}
val screenshotNotif by lazy {
    Notif(
        id = 101,
        title = "GKD",
        text = "截屏服务正在运行",
        ongoing = true,
        autoCancel = false,
    )
}

val floatingNotif by lazy {
    Notif(
        id = 102,
        title = "GKD",
        text = "悬浮窗按钮正在显示",
        ongoing = true,
        autoCancel = false,
    )
}

val httpNotif by lazy {
    Notif(
        id = 103,
        title = "GKD",
        text = "HTTP服务正在运行",
        ongoing = true,
        autoCancel = false,
    )
}