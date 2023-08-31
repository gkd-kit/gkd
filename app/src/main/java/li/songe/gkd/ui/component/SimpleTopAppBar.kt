package li.songe.gkd.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import li.songe.gkd.util.SafeR

@Composable
fun SimpleTopAppBar(
    @DrawableRes iconId: Int = SafeR.ic_back,
    onClickIcon: (() -> Unit)? = null,
    actions: @Composable() (RowScope.() -> Unit) = {},
    title: String,
) {
    TopAppBar(backgroundColor = Color(0xfff8f9f9), navigationIcon = {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = {
                onClickIcon?.invoke()
            }) {
                Icon(
                    painter = painterResource(id = iconId),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }, title = { Text(text = title) }, actions = actions
    )
}