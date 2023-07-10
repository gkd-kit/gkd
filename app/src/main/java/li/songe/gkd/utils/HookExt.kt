package li.songe.gkd.utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import li.songe.gkd.data.Value
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun useNavigateForQrcodeResult(): suspend () -> ScanIntentResult {
    val resolve = remember {
        Value { _: ScanIntentResult -> }
    }
    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            resolve.value(result)
        }
    return remember {
        suspend {
            scanLauncher.launch(ScanOptions().apply {
                setOrientationLocked(false)
                setBeepEnabled(false)
            })
            suspendCoroutine { continuation ->
                resolve.value = { s -> continuation.resume(s) }
            }
        }
    }
}

