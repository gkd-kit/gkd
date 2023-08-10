package li.songe.gkd.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun useNavigateForQrcodeResult(): suspend () -> ScanIntentResult {
    var resolve: ((ScanIntentResult) -> Unit)? = null
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        resolve?.invoke(result)
    }
    return remember {
        suspend {
            scanLauncher.launch(ScanOptions().apply {
                setOrientationLocked(false)
                setBeepEnabled(false)
            })
            suspendCoroutine { continuation ->
                resolve = { s -> continuation.resume(s) }
            }
        }
    }
}

