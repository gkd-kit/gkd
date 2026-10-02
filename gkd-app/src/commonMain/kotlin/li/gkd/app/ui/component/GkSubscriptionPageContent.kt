package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import li.gkd.app.resources.Res
import li.gkd.app.resources.subscription_load_failed
import li.gkd.app.resources.subscription_title
import li.gkd.app.state.Loadable
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.text.subscriptionMessageResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T : Any> GkSubscriptionPageContent(
    stateFlow: StateFlow<Loadable<T>>,
    onBack: () -> Unit,
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
        Loadable.Loading -> SubscriptionStatePage(onBack = onBack)
        is Loadable.Failure -> SubscriptionStatePage(
            onBack = onBack,
            message = current.cause.subscriptionMessageResource() ?: stringResource(Res.string.subscription_load_failed),
        )

        is Loadable.Ready -> content(current.value)
    }
}

@Composable
private fun SubscriptionStatePage(onBack: () -> Unit, message: String? = null) {
    GkScaffold(
        topBar = {
            GkTopAppBar(
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = onBack,
                    )
                },
                title = { Text(stringResource(Res.string.subscription_title)) },
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
