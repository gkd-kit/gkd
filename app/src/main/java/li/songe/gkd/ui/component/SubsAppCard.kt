package li.songe.gkd.ui.component

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
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.util.SafeR


@Composable
fun SubsAppCard(
    appRaw: SubscriptionRaw.AppRaw,
    appInfo: AppInfo? = null,
    subsConfig: SubsConfig? = null,
    onClick: (() -> Unit)? = null,
    onValueChange: ((Boolean) -> Unit)? = null,
) {
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
            painter = if (appInfo?.icon != null) rememberDrawablePainter(appInfo.icon) else painterResource(
                SafeR.ic_app_2
            ), contentDescription = null, modifier = Modifier
                .fillMaxHeight()
                .clip(CircleShape)
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
                text = appInfo?.name ?: appRaw.name ?: appRaw.id,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = appRaw.groups.size.toString() + "组规则",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            subsConfig?.enable ?: true,
            onValueChange,
        )
    }
}


