package li.songe.gkd.router

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

interface Page<P, R> {
    val path: String
    val defaultParams: P
    val content: @Composable BoxScope.(params: P, router: Router<R>) -> Unit
}