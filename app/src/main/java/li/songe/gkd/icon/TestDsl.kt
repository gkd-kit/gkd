package li.songe.gkd.icon

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Preview
@Composable
fun PreviewTestDsl() {
    val vectorString = """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8l8,8l1.41,-1.41L7.83,13H20v-2z" />
</vector>
    """.trim()
    val drawable = Drawable.createFromStream(vectorString.byteInputStream(), "ic_back")
    if (drawable != null) {
        Image(painter = rememberDrawablePainter(drawable = drawable), contentDescription = null)
    } else {
        Text(text = "null drawable")
    }
}