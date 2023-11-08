package li.songe.gkd.composition

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

open class CompositionActivity(
    private val block: CompositionActivity.(Bundle?) -> Unit,
) : ComponentActivity(), CanOnDestroy, CanOnConfigurationChanged {

    private val destroyHooks by lazy { linkedSetOf<() -> Unit>() }
    override fun onDestroy(f: () -> Unit) = destroyHooks.add(f)
    override fun onDestroy() {
        super.onDestroy()
        destroyHooks.forEach { f -> f() }
    }

    private val finishHooks by lazy { linkedSetOf<(fs: () -> Unit) -> Unit>() }
    fun onFinish(f: (fs: () -> Unit) -> Unit) = finishHooks.add(f)
    override fun finish() {
        if (finishHooks.isEmpty()) {
            super.finish()
        }
        val fs = { super.finish() }
        finishHooks.forEach { f -> f(fs) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        block(savedInstanceState)
    }

    private val configurationChangedHooks by lazy { linkedSetOf<(newConfig: Configuration) -> Unit>() }
    override fun onConfigurationChanged(f: (newConfig: Configuration) -> Unit) =
        configurationChangedHooks.add(f)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChangedHooks.forEach { f -> f(newConfig) }
    }

}