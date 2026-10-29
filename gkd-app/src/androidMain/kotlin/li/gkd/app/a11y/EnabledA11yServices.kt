package li.gkd.app.a11y

import android.content.ComponentName
import android.database.ContentObserver
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.app
import li.gkd.app.service.A11yService
import li.gkd.app.util.mapState

fun useEnabledA11yServicesFlow(scope: CoroutineScope): StateFlow<Set<ComponentName>> {
    val initialValue = app.getSecureA11yServices()
    return callbackFlow {
            val contextObserver =
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(app.getSecureA11yServices())
                    }
                }
            app.registerObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                contextObserver,
            )
            trySend(initialValue)
            awaitClose { app.unregisterObserver(contextObserver) }
        }
        .stateIn(scope, SharingStarted.Eagerly, initialValue)
}

fun useA11yServiceEnabledFlow(
    scope: CoroutineScope,
    servicesFlow: StateFlow<Set<ComponentName>> = useEnabledA11yServicesFlow(scope),
): StateFlow<Boolean> {
    return servicesFlow.mapState(scope) {
        it.contains(A11yService.a11yCn)
    }
}
