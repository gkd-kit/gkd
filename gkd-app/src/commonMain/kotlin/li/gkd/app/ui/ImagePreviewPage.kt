package li.gkd.app.ui

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.image.appImageLoader
import li.gkd.app.ui.navigation.ImagePreviewRoute
import li.gkd.app.ui.page.GkImagePreviewContent
import li.gkd.app.ui.platform.GkSystemBars
import li.gkd.app.ui.platform.UiHost

@Composable
fun ImagePreviewPage(
    host: UiHost,
    route: ImagePreviewRoute,
) {
    val mainVm = MainViewModel.requireCurrent()
    val imageLoader = appImageLoader()
    GkImagePreviewContent(
        route = route,
        onBack = mainVm.navigator::pop,
        imageLoader = imageLoader,
        systemBars = { host.GkSystemBars(it) },
        actionContent = { uri, _ ->
            if (uri != null && (uri.startsWith("https://", true) || uri.startsWith(
                    "http://",
                    true
                ))
            ) {
                GkIconButton(
                    GkIcons.OpenInNew, onClick = { mainVm.textDialog.showUrl(uri) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                )
            }
        },
    )
}
