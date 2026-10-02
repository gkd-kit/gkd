package li.gkd.app.priv

data class ShellCommandResult(
    val code: Int,
    val result: String,
    val error: String?
) {
    val ok: Boolean
        get() = code == 0
}
