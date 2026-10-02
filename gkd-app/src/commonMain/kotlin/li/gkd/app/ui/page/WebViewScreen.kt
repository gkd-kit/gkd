package li.gkd.app.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import li.gkd.app.resources.Res
import li.gkd.app.resources.link_copy
import li.gkd.app.resources.link_open_external
import li.gkd.app.resources.webview_reload
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.LocalOverlayBackHandler
import li.gkd.app.ui.style.iconTextSize
import org.jetbrains.compose.resources.stringResource

@Composable
fun WebViewScreen(
    title: String, loading: Boolean, onBack: () -> Unit,
    onReload: () -> Unit, onCopyLink: () -> Unit, onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier, onCompatibilityNotice: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    GkScaffold(modifier = modifier, topBar = {
        GkTopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                GkIconButton(
                    imageVector = GkIcons.ArrowBack,
                    onClick = { onBack() },
                )
            },
            title = {
                val loadingState = loading
                if (loadingState) {
                    CircularProgressIndicator(
                        modifier = Modifier.iconTextSize(),
                    )
                } else {
                    Text(
                        // webViewState.pageTitle 在调用 reload 后会变成 null
                        text = title,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                if (onCompatibilityNotice != null) {
                    GkIconButton(
                        imageVector = GkIcons.WarningAmber,
                        onClick = onCompatibilityNotice,
                    )
                }
                var expanded by remember { mutableStateOf(false) }
                if (expanded) LocalOverlayBackHandler.current { expanded = false }
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    GkIconButton(imageVector = GkIcons.MoreVert, onClick = { expanded = true })
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (!loading) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(Res.string.webview_reload))
                                },
                                onClick = {
                                    expanded = false
                                    onReload()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(Res.string.link_copy))
                            },
                            onClick = {
                                expanded = false
                                onCopyLink()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(Res.string.link_open_external))
                            },
                            onClick = {
                                expanded = false
                                onOpenExternal()
                            }
                        )
                    }
                }
            }
        )
    }) { contentPadding ->
        content(contentPadding)
    }
}
