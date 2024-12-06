package li.songe.gkd.notif

import li.songe.gkd.app
import li.songe.gkd.util.SafeR


data class Notif(
    val channel: NotifChannel,
    val id: Int,
    val smallIcon: Int = SafeR.ic_status,
    val title: String = app.getString(SafeR.app_name),
    val text: String,
    val ongoing: Boolean,
    val autoCancel: Boolean,
    val uri: String? = null,
)

val abNotif by lazy {
    Notif(
        channel = defaultChannel,
        id = 100,
        text = "无障碍正在运行",
        ongoing = true,
        autoCancel = false,
    )
}

val screenshotNotif by lazy {
    Notif(
        channel = screenshotChannel,
        id = 101,
        text = "截屏服务正在运行",
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
    )
}

val floatingNotif by lazy {
    Notif(
        channel = floatingChannel,
        id = 102,
        text = "悬浮窗按钮正在显示",
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
    )
}

val httpNotif by lazy {
    Notif(
        channel = httpChannel,
        id = 103,
        text = "HTTP服务正在运行",
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
    )
}

val snapshotNotif by lazy {
    Notif(
        channel = snapshotChannel,
        id = 104,
        text = "快照已保存至记录",
        ongoing = false,
        autoCancel = true,
        uri = "gkd://page/2",
    )
}

val snapshotActionNotif by lazy {
    Notif(
        channel = snapshotActionChannel,
        id = 105,
        text = "快照服务正在运行",
        ongoing = true,
        autoCancel = false,
    )
}