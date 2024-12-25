package li.songe.gkd.util

import com.blankj.utilcode.util.ClipboardUtils

fun copyText(text: String) {
    ClipboardUtils.copyText(text)
    toast("复制成功")
}