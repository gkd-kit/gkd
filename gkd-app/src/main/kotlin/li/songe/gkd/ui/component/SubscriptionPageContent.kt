package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import li.songe.gkd.ui.share.Loadable
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.scaffoldPadding

@Composable
fun <T : Any> SubscriptionPageContent(
    stateFlow: StateFlow<Loadable<T>>,
    content: @Composable (T) -> Unit,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    when (val current = state) {
        Loadable.Loading -> SubscriptionStatePage()
        is Loadable.Failure -> SubscriptionStatePage(
            message = current.cause.message ?: "订阅加载失败",
        )

        is Loadable.Ready -> content(current.value)
    }
}

@Composable
private fun SubscriptionStatePage(message: String? = null) {
    val mainVm = LocalMainViewModel.current
    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = { Text("订阅") },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .scaffoldPadding(contentPadding)
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (message == null) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
