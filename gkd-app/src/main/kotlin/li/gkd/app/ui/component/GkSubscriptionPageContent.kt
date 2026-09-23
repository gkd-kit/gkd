package li.gkd.app.ui.component

import li.gkd.app.MainViewModel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.text.UiStrings
import li.gkd.app.core.state.Loadable
import li.gkd.app.ui.style.scaffoldPadding

@Composable
fun <T : Any> GkSubscriptionPageContent(
    stateFlow: StateFlow<Loadable<T>>,
    retainContent: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    var lastReady by remember(stateFlow) { mutableStateOf<Loadable.Ready<T>?>(null) }
    val ready = state as? Loadable.Ready<T>
    if (!retainContent && ready != null) {
        SideEffect { lastReady = ready }
    }
    // An explicit removal may invalidate this page before its navigation exit ends.
    // Keep this purely visual snapshot; failures still render normally otherwise.
    when (val current = if (retainContent) lastReady ?: state else state) {
        Loadable.Loading -> SubscriptionStatePage()
        is Loadable.Failure -> SubscriptionStatePage(
            message = current.cause.message ?: UiStrings.subscription_load_failed,
        )

        is Loadable.Ready -> content(current.value)
    }
}

@Composable
private fun SubscriptionStatePage(message: String? = null) {
    val mainVm = MainViewModel.requireCurrent()
    Scaffold(
        topBar = {
            GkTopAppBar(
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = { Text(UiStrings.subscription_title) },
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
