package li.gkd.app.data

import android.os.Build
import com.hjq.device.compat.DeviceBrand
import com.hjq.device.compat.DeviceMarketName
import com.hjq.device.compat.DeviceOs
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.logging.LogMetadata

object AndroidLogMetadata {
    val deviceDescription by lazy {
        listOf(
            Build.MANUFACTURER,
            Build.MODEL,
            DeviceBrand.getBrandName(),
            DeviceOs.getOsName() + DeviceOs.getOsVersionName() + DeviceOs.getOsBigVersionCode(),
            DeviceMarketName.getMarketName(app)
        ).joinToString("/")
    }

    fun create() = LogMetadata(
        systemName = "Android",
        systemVersion = "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
        device = deviceDescription,
        app = "${META.versionName} (${META.versionCode})",
    )
}
