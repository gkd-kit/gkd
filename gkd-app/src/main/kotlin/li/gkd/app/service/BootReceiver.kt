package li.gkd.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机时拉起 [StatusService]。
 *
 * 使用自动化(Shizuku)模式时应用不启用无障碍服务, 系统因此没有任何理由在开机后启动本应用,
 * 表现为重启后没有常驻通知、Shizuku 未连接。这里显式补上开机入口。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        StatusService.autoStart()
    }
}
