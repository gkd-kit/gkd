package li.gkd.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel

class InterfaceSettingsViewModel : ViewModel() {
    // 选中项立即反馈；实际主题和语言在菜单关闭后生效，因此短暂独立于持久化设置。
    val selectedTheme: StateFlow<Boolean?>
        field = MutableStateFlow(SettingsRepository.settings.value.enableDarkTheme)
    val selectedLanguage: StateFlow<String?>
        field = MutableStateFlow(SettingsRepository.settings.value.languageTag)
    private var themeUpdateJob: Job? = null
    private var languageUpdateJob: Job? = null

    init {
        viewModelScope.launch {
            SettingsRepository.settings
                .map { it.enableDarkTheme }
                .distinctUntilChanged()
                .collect {
                    selectedTheme.value = it
                }
        }
        viewModelScope.launch {
            SettingsRepository.settings
                .map { it.languageTag }
                .distinctUntilChanged()
                .collect {
                    selectedLanguage.value = it
                }
        }
    }

    fun setTheme(value: Boolean?) {
        selectedTheme.value = value
        themeUpdateJob?.cancel()
        themeUpdateJob =
            MainViewModel.requireCurrent().scope.launch {
                // 给菜单关闭动画和用户感知留出时间，避免退场中立刻切换主题产生卡顿感。
                // 使用主界面作用域，离开此页也会完成已选设置的写入。
                delay(300)
                SettingsRepository.updateSettings { it.copy(enableDarkTheme = value) }
            }
    }

    fun setLanguage(value: String?) {
        selectedLanguage.value = value
        languageUpdateJob?.cancel()
        languageUpdateJob =
            MainViewModel.requireCurrent().scope.launch {
                // 给菜单关闭动画和用户感知留出时间，避免退场中立刻切换语言产生卡顿感。
                // 使用主界面作用域，离开此页也会完成已选设置的写入。
                delay(300)
                SettingsRepository.updateSettings { it.copy(languageTag = value) }
            }
    }
}
