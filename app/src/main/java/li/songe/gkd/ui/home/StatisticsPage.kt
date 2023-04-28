package li.songe.gkd.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatisticsPage() {
    Column(
        modifier = Modifier
            .padding(20.dp, 0.dp)
    ) {
        Text(text = "Statistics")
    }
}