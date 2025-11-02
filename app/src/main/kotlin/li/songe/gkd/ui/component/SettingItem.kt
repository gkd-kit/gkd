package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.util.throttle

@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    suffix: String? = null,
    suffixUnderline: Boolean = false,
    onSuffixClick: (() -> Unit)? = null,
    imageVector: ImageVector? = PerfIcon.KeyboardArrowRight,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .let {
                if (onClick != null) {
                    it.clickable(
                        onClick = throttle(fn = onClick),
                        onClickLabel = onClickLabel ?: "进入${title}页面"
                    )
                } else {
                    it
                }
            }
            .fillMaxWidth()
            .itemPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = if (imageVector != null) Modifier.weight(1f) else Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                if (suffix != null) {
                    Row {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = suffix,
                            style = MaterialTheme.typography.bodyMedium.run {
                                if (suffixUnderline) {
                                    copy(textDecoration = TextDecoration.Underline)
                                } else {
                                    this
                                }
                            },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = if (onSuffixClick != null) Modifier.clickable(
                                onClick = throttle(fn = onSuffixClick),
                            ) else Modifier
                        )
                    }
                } else {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (imageVector != null) {
            PerfIcon(imageVector = imageVector)
        }
    }
}