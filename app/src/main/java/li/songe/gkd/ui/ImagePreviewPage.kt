package li.songe.gkd.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.ToastUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import li.songe.gkd.util.LaunchedEffectTry
import li.songe.gkd.util.ProfileTransitions

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun ImagePreviewPage(
    filePath: String?,
) {
    val (bitmap, setBitmap) = remember {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffectTry {
        if (filePath != null) {
            setBitmap(withContext(IO) { BitmapFactory.decodeFile(filePath).asImageBitmap() })
        } else {
            ToastUtils.showShort("图片加载失败")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp)
            )
        }
    }
}