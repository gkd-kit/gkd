package li.songe.gkd.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.blankj.utilcode.util.ToastUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import li.songe.gkd.utils.LaunchedEffectTry

@RootNavGraph
@Destination
@Composable
fun ImagePreviewPage(
    filePath: String?
) {
    val context = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    var bitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffectTry {
        if (filePath != null) {
            bitmap = withContext(IO) { BitmapFactory.decodeFile(filePath) }
        } else {
            ToastUtils.showShort("图片路径缺失")
        }
    }

    bitmap?.let { bitmapVal ->
        Image(
            bitmap = bitmapVal.asImageBitmap(),
            contentDescription = null,
            Modifier.fillMaxWidth()
        )
    }
}