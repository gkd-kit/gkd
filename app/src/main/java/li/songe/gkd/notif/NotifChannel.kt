package li.songe.gkd.notif

import li.songe.gkd.app

data class NotifChannel(
    val id: String,
    val name: String,
    val desc: String,
)

val defaultChannel by lazy {
    NotifChannel(
        id = "default", name = "GKD", desc = "显示服务运行状态"
    )
}

val floatingChannel by lazy {
    NotifChannel(
        id = "floating", name = "悬浮窗按钮服务", desc = "用于主动捕获屏幕快照的悬浮窗按钮"
    )
}
val screenshotChannel by lazy {
    NotifChannel(
        id = "screenshot", name = "截屏服务", desc = "用于捕获屏幕截屏生成快照"
    )
}
val httpChannel by lazy {
    NotifChannel(
        id = "http", name = "HTTP服务", desc = "用于连接Web端工具调试"
    )
}

fun initChannel() {
    createChannel(app, defaultChannel)
    createChannel(app, floatingChannel)
    createChannel(app, screenshotChannel)
    createChannel(app, httpChannel)
}