package li.gkd.app.ui.component

import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// Each opening has its own identity, including reopening the same item during exit.
class SheetRequest<K : Any>(val key: K)

private data class SheetPresentation<K : Any, T : Any>(
    val request: SheetRequest<K>,
    val snapshot: T,
)

@Composable
fun <K : Any, T : Any> GkRetainedSheet(
    request: SheetRequest<K>?,
    snapshot: T?,
    missing: Boolean,
    onDismissRequest: (SheetRequest<K>) -> Unit,
    retainContent: Boolean = false,
    content: @Composable (SheetRequest<K>, T, SheetState, () -> Unit) -> Unit,
) {
    var retained by remember { mutableStateOf<SheetPresentation<K, T>?>(null) }
    val live = if (request != null && snapshot != null && !missing && !retainContent) {
        SheetPresentation(request, snapshot)
    } else null
    if (live != null) {
        SideEffect { retained = live }
    }
    LaunchedEffect(request, missing) {
        if (request != null && missing) onDismissRequest(request)
    }
    val presentation = live ?: retained
    if (presentation != null) {
        key(presentation.request) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val closing = request !== presentation.request || missing
            LaunchedEffect(closing) {
                if (closing) {
                    sheetState.hide()
                    if (!sheetState.isVisible && retained?.request === presentation.request) {
                        retained = null
                    }
                }
            }
            content(presentation.request, presentation.snapshot, sheetState) {
                onDismissRequest(presentation.request)
            }
        }
    }
}
