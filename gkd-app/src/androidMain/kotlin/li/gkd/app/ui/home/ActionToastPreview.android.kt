package li.gkd.app.ui.home

actual fun previewActionToast(text: String, system: Boolean) =
    li.gkd.app.util.AndroidToastUtils.previewAction(text, system)
