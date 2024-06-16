package li.songe.gkd.composition

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.blankj.utilcode.util.BarUtils

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
        fixTopPadding()
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

fun ComponentActivity.fixTopPadding() {
    // 当调用系统分享时, 会导致状态栏区域消失, 应用整体上移, 设置一个 top padding 保证不上移
    var tempTop: Int? = null
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, windowInsets ->
        view.setBackgroundColor(Color.TRANSPARENT)
        val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        if (statusBars.top == 0) {
            view.setPadding(
                statusBars.left,
                tempTop ?: BarUtils.getStatusBarHeight(),
                statusBars.right,
                statusBars.bottom
            )
        } else {
            tempTop = statusBars.top
            view.setPadding(statusBars.left, 0, statusBars.right, statusBars.bottom)
        }
        ViewCompat.onApplyWindowInsets(view, windowInsets)
    }
}
