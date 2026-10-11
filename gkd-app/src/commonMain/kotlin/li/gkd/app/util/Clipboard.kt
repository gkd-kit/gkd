package li.gkd.app.util

import li.gkd.app.platform.writeClipboardText
import li.gkd.app.resources.*

fun copyText(text: String) {
    writeClipboardText(text)
    ToastUtils.show(Res.string.copy_success.getSync())
}
