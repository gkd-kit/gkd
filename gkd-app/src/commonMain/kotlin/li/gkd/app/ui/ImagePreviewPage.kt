package li.gkd.app.ui

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.navigation.ImagePreviewRoute
import li.gkd.app.ui.page.GkImagePreviewContent

@Composable
fun ImagePreviewPage(
    route: ImagePreviewRoute,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    imageLoader: ImageLoader,
    systemBars: @Composable (Boolean) -> Unit,
) {
    GkImagePreviewContent(
        route = route, onBack = onBack, imageLoader = imageLoader, systemBars = systemBars,
        actionContent = { uri, _ ->
            if (uri != null && (uri.startsWith("https://", true) || uri.startsWith(
                    "http://",
                    true
                ))
            ) {
                GkIconButton(
                    GkIcons.OpenInNew, onClick = { onOpenUrl(uri) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                )
            }
        },
    )
}
