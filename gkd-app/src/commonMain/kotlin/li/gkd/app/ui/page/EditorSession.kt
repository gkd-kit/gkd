package li.gkd.app.ui.page

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** A host owns navigation, keyboard dismissal and persistence; the page owns its draft. */
data class EditorSession(
    val title: String,
    val hasChanges: suspend () -> Boolean,
    val saveEnabled: Boolean = true,
    val onSave: suspend () -> Unit,
    val titleContent: @Composable () -> Unit = { Text(title) },
)

