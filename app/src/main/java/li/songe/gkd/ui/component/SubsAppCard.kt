package li.songe.gkd.ui.component

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    sub: SubsAppCardData,
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
            painter = rememberDrawablePainter(sub.icon),
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
                text = sub.appName, maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(loading, highlight = PlaceholderHighlight.fade())
            )
            Text(
                text = sub.subsConfig.appId, maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(loading, highlight = PlaceholderHighlight.fade())
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            sub.subsConfig.enable,
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

