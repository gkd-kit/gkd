package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.util.throttle

@Composable
fun TextSwitch(
    modifier: Modifier = Modifier,
    title: String,
    paddingDisabled: Boolean = false,
    subtitle: String? = null,
    suffix: String? = null,
    suffixUnderline: Boolean = false,
    onSuffixClick: (() -> Unit)? = null,
    suffixIcon: (@Composable () -> Unit)? = null,
    checked: Boolean = true,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = { onCheckedChange?.invoke(!checked) },
    onClickLabel: String? = "切换${title}状态",
) {
    val topModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick, onClickLabel = onClickLabel)
    } else {
        modifier
    }
    Row(
        modifier = if (paddingDisabled) topModifier else topModifier.itemPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                if (suffix != null) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = suffix, style = MaterialTheme.typography.bodyMedium.run {
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
        suffixIcon?.invoke()
        PerfSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange?.let { throttle(fn = it) },
            modifier = Modifier.semantics {
                this.stateDescription = title + if (checked) "已开启" else "已关闭"
            }
        )
    }
}
