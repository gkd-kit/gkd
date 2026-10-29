package li.gkd.app.ui.home

import li.gkd.app.util.AndroidToastUtils

actual fun previewActionToast(text: String, system: Boolean) =
    AndroidToastUtils.previewAction(text, system)
