package li.songe.gkd.hooks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.blankj.utilcode.util.LogUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.data.Value
import li.songe.gkd.util.Singleton
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

@Composable
fun useFetchSubs(): suspend (String) -> String {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    return remember {
        { url ->
            loading
            Singleton.client.get(url).bodyAsText()
        }
    }
}
