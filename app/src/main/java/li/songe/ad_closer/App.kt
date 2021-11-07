package li.songe.ad_closer

import android.app.Application
import com.blankj.utilcode.util.LogUtils

class App:Application() {
    override fun onCreate() {
        super.onCreate()
        LogUtils.d("onCreate")
    }
}