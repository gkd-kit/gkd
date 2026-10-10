package li.gkd.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleSectionHeader(title: String, groupCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics { heading() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(Res.string.category_group_count, groupCount.toString()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
