package li.gkd.app.entry

import android.content.pm.PackageManager
import androidx.core.net.toUri
import li.gkd.app.app
import li.gkd.app.util.extraCptName

class OpenTileActivity : EntryActivity() {
    override fun prepareIntent() {
        val qsTileCpt = intent?.extraCptName
        if (qsTileCpt != null && intent.data == null) {
            val serviceInfo =
                app.packageManager.getServiceInfo(qsTileCpt, PackageManager.GET_META_DATA)
            val uriValue = serviceInfo.metaData.getString("QS_TILE_URI")
            if (uriValue != null) {
                intent.data = uriValue.toUri()
            }
        }
    }
}
