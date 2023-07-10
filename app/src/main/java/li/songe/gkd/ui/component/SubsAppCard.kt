package li.songe.gkd.ui.component

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder
import li.songe.gkd.R
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.data.getAppInfo
import li.songe.gkd.utils.SafeR


@Composable
fun SubsAppCard(
    loading: Boolean = false,
    sub: SubsAppCardData,
    onClick: (() -> Unit)? = null,
    onValueChange: ((Boolean) -> Unit)? = null
) {
    val info = getAppInfo(sub.appRaw.id)
    Row(
        modifier = Modifier
            .height(60.dp)
            .clickable {
                onClick?.invoke()
            }
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Image(
            painter = if (info.icon != null) rememberDrawablePainter(info.icon) else painterResource(
                SafeR.ic_app_2
            ),
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
                text = info.name ?: "-", maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(loading, highlight = PlaceholderHighlight.fade())
            )
            Text(
                text = sub.appRaw.groups.size.toString() + "组规则", maxLines = 1,
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
    val subsConfig: SubsConfig,
    val appRaw: SubscriptionRaw.AppRaw
)

