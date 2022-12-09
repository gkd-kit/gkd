package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Text114514() {
    CompositionLocalProvider(
        LocalTextStyle provides TextStyle(fontSize = 80.sp)
    ) {
        Row {
            Text("ping")
            Text("pong")
        }
    }
}

@Preview(backgroundColor = 0xFFFFFFFF, showBackground = true)
@Composable
fun Text114514Preview() {
    Surface(modifier = Modifier.width(200.dp)) {
        Text114514()
    }
}