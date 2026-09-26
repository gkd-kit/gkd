package li.gkd.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.data.fixedName
import li.gkd.app.data.isStateChanged
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.util.format
import li.gkd.db.A11yEventLog

@Composable
fun GkEventLogOverlayCard(eventLog: A11yEventLog, modifier: Modifier = Modifier) {
    val railColor = MaterialTheme.colorScheme.secondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(color = railColor, size = Size(2.dp.toPx(), size.height))
            }
    ) {
        Spacer(modifier = Modifier.width(6.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GkFixedTimeText(
                    text = eventLog.ctime.format("HH:mm:ss SSS"),
                )
                Spacer(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .size(height = 8.dp, width = 1.dp)
                )
                GkAppNameText(
                    appId = eventLog.appId,
                )
            }
            Text(
                text = eventLog.fixedName,
                color = if (eventLog.isStateChanged) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.MiddleEllipsis,
            )
            val desc = eventLog.desc
            if (desc != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    GkIcon(
                        imageVector = GkIcons.Title,
                        modifier = Modifier.iconTextSize(
                            square = false
                        ),
                    )
                    Text(
                        text = desc,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                            .padding(horizontal = 2.dp),
                    )
                }
            }
            if (eventLog.text.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    GkIcon(
                        imageVector = GkIcons.TextFields,
                        modifier = Modifier.iconTextSize(
                            square = false
                        ),
                    )
                    // 如果祖先容器有设置了 height(IntrinsicSize.Min) 会导致 FlowRow 不会自动换行
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        eventLog.text.forEach { subText ->
                            Text(
                                text = subText,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    )
                                    .padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
