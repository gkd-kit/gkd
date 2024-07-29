package li.songe.gkd.ui.component

import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

data class DialogOptions internal constructor(
    val onDismissRequest: (() -> Unit)? = null,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable (() -> Unit)? = null,
    val icon: @Composable (() -> Unit)? = null,
    val title: @Composable (() -> Unit)? = null,
    val text: @Composable (() -> Unit)? = null,
)


class DialogApiInjection(
    val create: (options: DialogOptions) -> Unit,
    val dismiss: () -> Unit,
)

fun DialogApiInjection.build(
    title: String,
    text: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    return create(
        DialogOptions(
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            icon = icon?.let {
                { Image(imageVector = icon, contentDescription = null) }
            },
            title = {
                Text(text = title)
            },
            text = {
                Text(text = text)
            },
        )
    )
}

@Composable
fun useDialog(): DialogApiInjection {
    var options by remember { mutableStateOf<DialogOptions?>(null) }
    val apiInjection = remember {
        DialogApiInjection(
            create = { options = it },
            dismiss = { options = null }
        )
    }
    options?.let {
        AlertDialog(
            onDismissRequest = it.onDismissRequest ?: apiInjection.dismiss,
            confirmButton = it.confirmButton,
            dismissButton = it.dismissButton,
            icon = it.icon,
            title = it.title,
            text = it.text,
        )
    }
    return apiInjection
}
