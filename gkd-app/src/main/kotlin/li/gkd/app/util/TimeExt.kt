package li.gkd.app.util

fun Long.format(formatStr: String): String = TimeUtils.formatDate(this, formatStr)
