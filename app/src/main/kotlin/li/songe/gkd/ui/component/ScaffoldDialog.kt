package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ScaffoldDialog(
    title: String,
    onClose: () -> Unit,
    content: @Composable (ColumnScope.() -> Unit)
) = FullscreenDialog(onDismissRequest = onClose) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PerfTopAppBar(
                title = { Text(text = title) },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Close,
                        onClick = onClose,
                    )
                },
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(it),
                content = content,
            )
        }
    )
}