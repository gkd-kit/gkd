package li.songe.gkd

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.net.toUri
import li.songe.gkd.util.extraCptName

class OpenTileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qsTileCpt = intent?.extraCptName
        if (qsTileCpt != null && intent.data == null) {
            val serviceInfo =
                app.packageManager.getServiceInfo(qsTileCpt, PackageManager.GET_META_DATA)
            val uriValue = serviceInfo.metaData.getString("QS_TILE_URI")
            if (uriValue != null) {
                intent.data = uriValue.toUri()
            }
        }
        navToMainActivity()
    }
}