package li.gkd.app.util

object ActionToastTemplate {
    fun render(template: String, ruleName: String, groupName: String, count: Long): String =
        template.replace($$"${1}", ruleName)
            .replace($$"${2}", groupName)
            .replace($$"${3}", count.toString())
}
