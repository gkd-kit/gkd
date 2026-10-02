package li.gkd.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import li.gkd.app.ui.component.LocalEditorWindowInsets
import li.gkd.app.ui.component.LocalTopBarWindowInsets
import li.gkd.app.ui.home.LocalHomeNavigationInsets
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Paint chrome over edge-to-edge content; shared scaffolds consume these insets once. */
@Composable
fun GkAndroidWindow(
    environment: DesktopEnvironment,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRecents: () -> Unit,
    content: @Composable () -> Unit
) {
    val system = environment.android
    CompositionLocalProvider(
        LocalTopBarWindowInsets provides WindowInsets(top = system.topInset.dp),
        LocalHomeNavigationInsets provides WindowInsets(bottom = system.bottomInset.dp),
        LocalEditorWindowInsets provides WindowInsets(bottom = system.bottomInset.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .padding(bottom = (if (system.imeVisible) system.imeHeight else 0f).dp)
            ) { content() }
            // Android system chrome does not follow the app's accessibility font scale.
            CompositionLocalProvider(
                LocalDensity provides Density(
                    LocalDensity.current.density,
                    1f
                )
            ) {
                if (system.statusBarVisible && system.statusBarHeight > 0f) {
                    GkAndroidStatusBar(system, environment.dark)
                }
                if (system.cutoutHeight > 0f) {
                    Canvas(
                        Modifier.align(Alignment.TopCenter)
                            .size(system.cutoutWidth.dp, system.cutoutHeight.dp)
                    ) {
                        drawRoundRect(Color.Black, cornerRadius = CornerRadius(size.height / 2))
                    }
                }
                if (system.imeVisible) {
                    Column(
                        Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = system.bottomInset.dp)
                            .fillMaxWidth().height(system.imeHeight.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("键盘区域（模拟）", style = MaterialTheme.typography.titleSmall)
                        Text("使用电脑键盘输入", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onBack) { Text("收起键盘") }
                    }
                }
                if (system.bottomInset > 0f && !system.gestureHandleVisible) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .height(system.bottomInset.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onBack,
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("◁") }
                        TextButton(
                            onClick = onHome,
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("○") }
                        TextButton(
                            onClick = onRecents,
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("□") }
                    }
                }
                if (system.bottomInset > 0f && system.gestureHandleVisible) {
                    Canvas(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .height(system.bottomInset.dp)
                            .semantics { contentDescription = "Android 手势导航条（模拟）" }) {
                        val width = minOf(108.dp.toPx(), size.width * 0.3f)
                        val height = minOf(4.dp.toPx(), size.height)
                        drawRoundRect(
                            if (environment.dark) Color.White else Color.Black,
                            topLeft = Offset((size.width - width) / 2, (size.height - height) / 2),
                            size = Size(width, height), cornerRadius = CornerRadius(height / 2)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GkAndroidStatusBar(system: AndroidWindow, dark: Boolean) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var time by remember { mutableStateOf(LocalTime.now().format(formatter)) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now().format(formatter)
            delay(1000)
        }
    }
    val ink = if (dark) Color.White else Color.Black
    Row(
        Modifier.fillMaxWidth().height(system.statusBarHeight.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(time, color = ink, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription =
                    "模拟状态：电量 ${system.batteryPercent}%，${if (system.charging) "充电中，" else ""}Wi-Fi ${if (system.wifi) "已连接" else "关闭"}，信号 ${system.mobileSignal} 格"
            }) {
            Canvas(Modifier.size(14.dp)) {
                for (i in 0..3) {
                    val h = size.height * (i + 1) / 4
                    drawRect(
                        ink.copy(alpha = if (i < system.mobileSignal) 1f else 0.25f),
                        Offset(size.width * i / 4, size.height - h), Size(size.width / 6, h)
                    )
                }
            }
            if (system.wifi) {
                Canvas(Modifier.size(16.dp)) {
                    for (radius in listOf(0.9f, 0.6f)) {
                        val diameter = size.width * radius * 2
                        drawArc(
                            ink, 225f, 90f, false,
                            Offset((size.width - diameter) / 2, size.height - diameter / 2),
                            Size(diameter, diameter), style = Stroke(1.8.dp.toPx())
                        )
                    }
                    drawCircle(
                        ink,
                        1.4.dp.toPx(),
                        Offset(size.width / 2, size.height - 1.5.dp.toPx())
                    )
                }
            }
            Text(
                "${system.batteryPercent}%",
                color = ink,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
            )
            Canvas(Modifier.size(22.dp, 11.dp)) {
                val bodyWidth = size.width - 2.dp.toPx()
                drawRoundRect(
                    ink,
                    size = Size(bodyWidth, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(1.dp.toPx())
                )
                drawRect(
                    ink,
                    Offset(bodyWidth, size.height / 3),
                    Size(2.dp.toPx(), size.height / 3)
                )
                val padding = 2.dp.toPx()
                drawRect(
                    if (system.charging) Color(0xFF34C759) else ink, Offset(padding, padding),
                    Size(
                        (bodyWidth - padding * 2) * system.batteryPercent / 100f,
                        size.height - padding * 2
                    )
                )
            }
        }
    }
}
