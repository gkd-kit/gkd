package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SnapshotCard() {
    Row {
        Text(text = "06-02 20:47:48")
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = "酷安")
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = "查看")
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = "分享")
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = "删除")
    }
}


@Preview
@Composable
fun PreviewSnapshotCard() {
    SnapshotCard()
}