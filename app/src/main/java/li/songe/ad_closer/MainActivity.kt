package li.songe.ad_closer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.blankj.utilcode.util.LogUtils
import li.songe.ad_closer.ui.theme.AdCloserTheme
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener
import android.content.pm.PackageManager


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdCloserTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Greeting("Android2")
                }
            }
            LogUtils.d(this)
            packageName
        }
        checkPermission(0)
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            LogUtils.d(requestCode, grantResult)
        }
//        Shizuku.bindUserService()

    }
    private fun checkPermission(code: Int): Boolean {
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            return false
        }
        return when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                // Granted
                true
            }
            Shizuku.shouldShowRequestPermissionRationale() -> {
                // Users choose "Deny and don't ask again"
                false
            }
            else -> {
                // Request the permission
                Shizuku.requestPermission(code)
                false
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name 4399")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AdCloserTheme {
        Greeting("React")
    }
}