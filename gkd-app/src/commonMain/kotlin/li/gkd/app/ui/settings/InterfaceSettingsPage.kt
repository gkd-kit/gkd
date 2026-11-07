package li.gkd.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.model.findOption
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTextMenu
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.option.DarkThemeOption
import li.gkd.app.ui.option.LanguageOption
import li.gkd.app.ui.platform.dynamicColorAvailable
import li.gkd.app.ui.style.titleItemPadding
import org.jetbrains.compose.resources.stringResource

@Composable
fun InterfaceSettingsPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { InterfaceSettingsViewModel() }
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val selectedTheme by vm.selectedTheme.collectAsStateWithLifecycle()
    val selectedLanguage by vm.selectedLanguage.collectAsStateWithLifecycle()
    val scroll = rememberColumnScrollState()
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scroll.scrollBehavior),
        topBar = {
            GkTopAppBar(
                title = { Text(stringResource(Res.string.settings_interface)) },
                navigationIcon = {
                    GkIconButton(imageVector = GkIcons.ArrowBack, onClick = mainVm.navigator::pop)
                },
                scrollBehavior = scroll.scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll.scrollState)) {
            Text(
                text = stringResource(Res.string.settings_appearance),
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GkTextMenu(
                title = stringResource(Res.string.theme_mode),
                option = DarkThemeOption.objects.findOption(selectedTheme),
                onOptionChange = { vm.setTheme(it.value) },
            )
            if (dynamicColorAvailable()) {
                GkTextSwitch(
                    title = stringResource(Res.string.dynamic_colors),
                    checked = store.enableDynamicColor,
                    onCheckedChange = { enabled ->
                        SettingsRepository.updateSettings { it.copy(enableDynamicColor = enabled) }
                    },
                )
            }
            Text(
                text = stringResource(Res.string.settings_language),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GkTextMenu(
                title = stringResource(Res.string.language_title),
                option = LanguageOption.objects.findOption(selectedLanguage),
                onOptionChange = { vm.setLanguage(it.value) },
            )
            GkPageBottomSpace()
        }
    }
}
