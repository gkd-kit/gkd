package li.songe.gkd.ui.share

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.KeyboardUtils

class ActivityImeController(
    private val activity: ComponentActivity,
) : DefaultLifecycleObserver {
    val showAnimationRunningFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val fullyHiddenFlow = MutableStateFlow(true)
    private var removeSoftInputChangedListener: (() -> Unit)? = null

    private val imeVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true // fix #1315

    private val animationCallback = object : WindowInsetsAnimationCompat.Callback(
        DISPATCH_MODE_CONTINUE_ON_SUBTREE,
    ) {
        override fun onStart(
            animation: WindowInsetsAnimationCompat,
            bounds: WindowInsetsAnimationCompat.BoundsCompat,
        ): WindowInsetsAnimationCompat.BoundsCompat {
            if (animation.isImeAnimation) {
                showAnimationRunningFlow.value = imeVisible
            }
            return bounds
        }

        override fun onProgress(
            insets: WindowInsetsCompat,
            runningAnimations: List<WindowInsetsAnimationCompat>,
        ): WindowInsetsCompat = insets

        override fun onEnd(animation: WindowInsetsAnimationCompat) {
            if (animation.isImeAnimation) {
                fullyHiddenFlow.value = !imeVisible
                showAnimationRunningFlow.value = false
            }
        }
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        if (AndroidTarget.R) {
            ViewCompat.setWindowInsetsAnimationCallback(
                activity.window.decorView,
                animationCallback,
            )
        } else {
            removeSoftInputChangedListener = KeyboardUtils.registerSoftInputChangedListener(
                activity.window,
            ) { height ->
                fullyHiddenFlow.value = height == 0
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (AndroidTarget.R) {
            ViewCompat.setWindowInsetsAnimationCallback(activity.window.decorView, null)
        } else {
            removeSoftInputChangedListener?.invoke()
            removeSoftInputChangedListener = null
        }
        fullyHiddenFlow.value = true
        showAnimationRunningFlow.value = false
        owner.lifecycle.removeObserver(this)
    }

    @MainThread
    fun requestHide(): Boolean {
        if (!imeVisible) {
            fullyHiddenFlow.value = true
            return false
        }
        fullyHiddenFlow.value = false
        KeyboardUtils.hideSoftInput(activity)
        return true
    }

    suspend fun hideAndAwait(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!requestHide()) return@withContext false
        fullyHiddenFlow.first { it }
        true
    }

    private val WindowInsetsAnimationCompat.isImeAnimation: Boolean
        get() = typeMask and WindowInsetsCompat.Type.ime() != 0
}
