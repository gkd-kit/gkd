package li.songe.gkd.ui.component

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder
import li.songe.gkd.db.table.SubsConfig


@Composable
fun SubsAppCard(
    loading: Boolean = false,
    args: SubsAppCardData,
    onValueChange: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .height(60.dp)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Image(
            rememberDrawablePainter(args.icon),
            contentDescription = null,
            modifier = Modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .placeholder(loading, highlight = PlaceholderHighlight.fade())
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = args.appName, maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(loading, highlight = PlaceholderHighlight.fade())
            )
            Text(
                text = args.subsConfig.appId, maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(loading, highlight = PlaceholderHighlight.fade())
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            args.subsConfig.enable,
            onValueChange,
            modifier = Modifier.placeholder(loading, highlight = PlaceholderHighlight.fade())
        )
    }
}

data class SubsAppCardData(
    val appName: String,
    val icon: Drawable,
    val subsConfig: SubsConfig,
)

