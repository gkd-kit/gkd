package li.songe.gkd.priv

import android.content.Context
import android.view.IWindowManager
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatWindowManager {
    val value: IWindowManager = IWindowManager.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.WINDOW_SERVICE),
        ),
    )

    companion object {
        private const val ROTATION_WITHOUT_CALLER = 1
        private const val ROTATION_WITH_CALLER = 2

        // https://diff.songe.li/i/IWindowManager.freezeRotation
        private val freezeRotationType by lazy {
            IWindowManager::class.detectHiddenMethod(
                "freezeRotation",
                ROTATION_WITHOUT_CALLER to listOf(Int::class),
                ROTATION_WITH_CALLER to listOf(Int::class, String::class),
            )
        }

        // https://diff.songe.li/i/IWindowManager.thawRotation
        private val thawRotationType by lazy {
            IWindowManager::class.detectHiddenMethod(
                "thawRotation",
                ROTATION_WITHOUT_CALLER to emptyList(),
                ROTATION_WITH_CALLER to listOf(String::class),
            )
        }
    }

    fun freezeRotation(rotation: Int, caller: String) = when (freezeRotationType) {
        ROTATION_WITHOUT_CALLER -> value.freezeRotation(rotation)
        ROTATION_WITH_CALLER -> value.freezeRotation(rotation, caller)
        else -> throw NoSuchMethodException("IWindowManager#freezeRotation")
    }

    fun thawRotation(caller: String) = when (thawRotationType) {
        ROTATION_WITHOUT_CALLER -> value.thawRotation()
        ROTATION_WITH_CALLER -> value.thawRotation(caller)
        else -> throw NoSuchMethodException("IWindowManager#thawRotation")
    }
}
