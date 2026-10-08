package li.gkd.app.util

import li.gkd.app.platform.writeClipboardText
import li.gkd.app.resources.Res
import li.gkd.app.resources.copy_success
import li.gkd.app.ui.text.getSync

fun copyText(text: String) {
    writeClipboardText(text)
    ToastUtils.show(Res.string.copy_success.getSync())
}
