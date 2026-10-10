package li.gkd.app.ui.settings

import li.gkd.app.resources.*
import li.gkd.app.ui.text.getSync

object SettingsFeedback {
    fun backupImported(skipped: Int): String = if (skipped > 0) {
        Res.string.backup_import_skipped_config_count.getSync(skipped)
    } else Res.string.import_success.getSync()
}
