package li.gkd.app.app

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

actual val launcherAppIdFlow: StateFlow<String> by lazy {
    AppInfoRepository.state.map { it.snapshot?.launcherAppId.orEmpty() }
        .stateIn(
            applicationScope(),
            SharingStarted.Eagerly,
            AppInfoRepository.snapshot?.launcherAppId.orEmpty()
        )
}
