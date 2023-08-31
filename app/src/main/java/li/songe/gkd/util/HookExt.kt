package li.songe.gkd.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.blankj.utilcode.util.LogUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class Ref<T>(var value: T)

@Composable
fun <T> useRef(init: T): Ref<T> {
    return remember {
        Ref(init)
    }
}

@Composable
fun useNavigateForQrcodeResult(): suspend () -> ScanIntentResult {
    val contract = remember { ScanContract() }
    val fc = useRef<((ScanIntentResult) -> Unit)?>(null)
    val scanLauncher = rememberLauncherForActivityResult(contract) { result ->
        fc.value?.invoke(result)
    }
    return remember {
        suspend {
            scanLauncher.launch(ScanOptions().apply {
                setOrientationLocked(false)
                setBeepEnabled(false)
            })
            suspendCoroutine { continuation ->
                fc.value = { s -> continuation.resume(s) }
            }
        }
    }
}



