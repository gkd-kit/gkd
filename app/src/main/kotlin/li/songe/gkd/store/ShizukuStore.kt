package li.songe.gkd.store

import kotlinx.serialization.Serializable
import li.songe.gkd.META

@Serializable
data class ShizukuStore(
    val versionCode: Int = META.versionCode,
    val enableActivity: Boolean = false,
    val enableTapClick: Boolean = false,
    val enableWorkProfile: Boolean = false,
) {
    val enableShizukuAnyFeat: Boolean
        get() = enableActivity || enableTapClick || enableWorkProfile
}
