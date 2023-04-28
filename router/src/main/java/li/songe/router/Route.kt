package li.songe.router

data class Route(
    val page: Page,
    val data: Any? = null,
    val onBack: (result: Any?) -> Unit
)
