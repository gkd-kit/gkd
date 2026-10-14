package li.gkd.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import li.gkd.app.ui.component.gkTopAppBarScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.external.externalSnapshotCommand
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkCopyableText
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.util.copyText
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExternalCallsPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { ExternalCallsViewModel() }
    val showTokenDialog by vm.showTokenDialog.collectAsStateWithLifecycle()
    val token by vm.externalToken.collectAsStateWithLifecycle()
    val confirmReset by vm.confirmTokenReset.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(imageVector = GkIcons.ArrowBack, onClick = mainVm.navigator::pop)
                },
                title = { Text(stringResource(Res.string.external_calls_title)) },
                actions = {
                    GkIconButton(
                        imageVector = GkIcons.Key,
                        contentDescription = stringResource(Res.string.external_calls_token_title),
                        onClick = { vm.setShowTokenDialog(true) },
                    )
                },
            )
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(Res.string.external_calls_notice),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val command = externalSnapshotCommand(token)
            OutlinedCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.external_calls_snapshot), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.external_calls_snapshot_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GkCopyableText(
                            text = AnnotatedString(command),
                            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 48.dp, bottom = 12.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            onCopy = ::copyText,
                        )
                    }
                }
            }
            GkPageBottomSpace()
        }
    }
    if (showTokenDialog) {
        GkAlertDialog(
            title = { Text(stringResource(Res.string.external_calls_token_title)) },
            onDismissRequest = { vm.setShowTokenDialog(false) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(Res.string.external_calls_token_hint))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GkCopyableText(
                            text = AnnotatedString(token),
                            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 48.dp, bottom = 12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            onCopy = ::copyText,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setShowTokenDialog(false) }) {
                    Text(stringResource(Res.string.action_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setConfirmTokenReset(true) }) {
                    Text(stringResource(Res.string.external_calls_reset_token))
                }
            },
        )
    }
    if (confirmReset) {
        GkAlertDialog(
            title = { Text(stringResource(Res.string.external_calls_reset_token)) },
            text = { Text(stringResource(Res.string.external_calls_reset_notice)) },
            onDismissRequest = { vm.setConfirmTokenReset(false) },
            confirmButton = {
                TextButton(onClick = vm::resetExternalToken) {
                    Text(stringResource(Res.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setConfirmTokenReset(false) }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}
