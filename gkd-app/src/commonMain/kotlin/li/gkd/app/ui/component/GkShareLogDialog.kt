package li.gkd.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_save_to_downloads
import li.gkd.app.resources.action_share
import li.gkd.app.resources.upload_generate_link
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkShareLogDialog(
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onUpload: () -> Unit
) {
    GkDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            val modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
            Text(
                text = stringResource(Res.string.action_share),
                modifier = Modifier
                    .clickable(onClick = onShare)
                    .then(modifier),
            )
            Text(
                text = stringResource(Res.string.action_save_to_downloads),
                modifier = Modifier
                    .clickable(onClick = onSave)
                    .then(modifier),
            )
            Text(
                text = stringResource(Res.string.upload_generate_link),
                modifier = Modifier
                    .clickable(onClick = onUpload)
                    .then(modifier),
            )
        }
    }
}
