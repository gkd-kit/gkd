package li.gkd.app

import kotlinx.serialization.Serializable

/** Android window geometry in logical dp. This is a UI host, not an Android runtime. */
@Serializable
data class AndroidWindow(
    val statusBarHeight: Float = defaultSimulatedDevice.statusBarHeight,
    val navigationBarHeight: Float = defaultSimulatedDevice.navigationBarHeight,
    val statusBarVisible: Boolean = true,
    val navigationBarVisible: Boolean = true,
    val gestureHandleVisible: Boolean = true,
    val cutoutWidth: Float = 0f,
    val cutoutHeight: Float = 0f,
    val batteryPercent: Int = defaultSimulatedDevice.batteryPercent,
    val charging: Boolean = false,
    val wifi: Boolean = true,
    val localNetworkGranted: Boolean = true,
    val mobileSignal: Int = defaultSimulatedDevice.mobileSignal,
    val serviceEnabled: Boolean = false,
    val automationRunning: Boolean = false,
    val a11yEnabled: Boolean = false,
    val partiallyDisabled: Boolean = false,
    val activityRunning: Boolean = false,
    val restricted: Boolean = false,
    val restrictedWarning: Boolean = false,
    val automationOccupied: Boolean = false,
    val topAppId: String = "",
    val privilegeAvailable: Boolean = true,
    val statusEnabled: Boolean = false,
    val ignoreBatteryOptimizations: Boolean = false,
    val writeSecureSettings: Boolean = false,
    val imeVisible: Boolean = false,
    val imeHeight: Float = defaultSimulatedDevice.imeHeight,
) {
    val topInset: Float get() = maxOf(if (statusBarVisible) statusBarHeight else 0f, cutoutHeight)
    val bottomInset: Float get() = if (navigationBarVisible) navigationBarHeight else 0f

    fun validate(width: Int, height: Int) {
        require(statusBarHeight in 0f..100f && navigationBarHeight in 0f..100f) { "Invalid system bar height" }
        require(cutoutWidth in 0f..(width / 2f) && cutoutHeight in 0f..100f) { "Invalid display cutout" }
        require((cutoutWidth == 0f) == (cutoutHeight == 0f)) { "Cutout requires both dimensions" }
        require(imeHeight in 100f..600f) { "Invalid IME height" }
        require(topInset + bottomInset + (if (imeVisible) imeHeight else 0f) < height) { "System bars cover viewport" }
        require(batteryPercent in 0..100 && mobileSignal in 0..4) { "Invalid simulated device status" }
    }
}
