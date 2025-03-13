package li.songe.gkd.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import li.songe.gkd.ui.style.itemHorizontalPadding


@Composable
fun CardFlagBar(
    visible: Boolean,
    width: Dp = itemHorizontalPadding
) {
    Row(
        modifier = Modifier
            .width(width)
            .height(16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        AnimatedVisibility(
            visible = visible,
        ) {
            Spacer(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.tertiary)
                    .fillMaxHeight()
                    .width(2.dp)
            )
        }
    }
}
