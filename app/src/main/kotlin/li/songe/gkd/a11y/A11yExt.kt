package li.songe.gkd.a11y

import android.content.ComponentName
import android.database.ContentObserver
import android.provider.Settings
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.songe.gkd.app
import li.songe.gkd.service.A11yService

context(vm: ViewModel)
fun useA11yServiceEnabledFlow(): StateFlow<Boolean> {
    val stateFlow = MutableStateFlow(getA11yServiceEnabled())
    val contextObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            stateFlow.value = getA11yServiceEnabled()
        }
    }
    app.contentResolver.registerContentObserver(
        Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        false,
        contextObserver
    )
    vm.addCloseable {
        app.contentResolver.unregisterContentObserver(contextObserver)
    }
    return stateFlow
}

private fun getA11yServiceEnabled(): Boolean {
    return (Settings.Secure.getString(
        app.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: "").split(';').any {
        ComponentName.unflattenFromString(it) == A11yService.a11yComponentName
    }
}