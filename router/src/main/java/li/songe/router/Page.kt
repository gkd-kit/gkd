package li.songe.router

import androidx.compose.runtime.Composable

open class Page(
    val name: String ="default_name",
    val content: @Composable () -> Unit,
) {
    @Composable
    inline operator fun invoke() {
        content()
    }
}