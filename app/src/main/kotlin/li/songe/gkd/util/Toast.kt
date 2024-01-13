package li.songe.gkd.util

import android.widget.Toast
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.appScope


private var lastToast: Toast? = null

fun toast(text: String) {
    appScope.launch(Dispatchers.Main) {
        try {
            lastToast?.cancel()
            lastToast = Toast.makeText(app, text, Toast.LENGTH_SHORT)
            lastToast!!.show()
        } catch (e: Exception) {
            LogUtils.d("toast失败", e)
        }
    }
}