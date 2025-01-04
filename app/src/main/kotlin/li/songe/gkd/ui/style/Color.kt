package li.songe.gkd.ui.style

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable


val surfaceCardColors: CardColors
    @Composable
    get() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

