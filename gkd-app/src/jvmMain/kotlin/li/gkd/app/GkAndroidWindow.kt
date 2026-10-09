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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import li.gkd.app.ui.component.LocalEditorWindowInsets
import li.gkd.app.ui.component.LocalTopBarWindowInsets
import li.gkd.app.ui.home.LocalHomeNavigationInsets
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Traced from the reference status bar; coordinates retain the source image's pixel proportions.
private val statusWifiPath = PathParser().parsePathString(
    "M3,12 C16,0 40,0 54,12 Q55,13 54,15 L51,18 Q50,19 48,17 " +
        "C37,8 21,8 9,17 Q7,19 5,17 L3,15 Q2,13 3,12 Z " +
        "M12,22 C20,14 35,13 45,21 Q46,23 44,25 L42,28 Q41,29 39,27 " +
        "C33,22 25,22 19,27 Q17,29 15,27 L12,25 Q11,24 12,22 Z " +
        "M21,31 Q28,26 36,31 L36,34 L30,40 Q28,41 26,39 L21,34 Q20,32 21,31 Z " +
        "M50,25 Q51,24 52,25 L56,30 Q57,32 55,32 L47,32 Q45,32 46,30 Z " +
        "M47,35 L55,35 Q57,35 56,37 L52,42 Q51,43 50,42 L46,37 Q45,35 47,35 Z"
).toPath()

private val statusBatteryPath = PathParser().parsePathString(
    "M14,3 H62 Q73,3 73,14 V16 H75 Q77,16 77,19 V26 Q77,29 75,29 H73 " +
        "V31 Q73,42 62,42 H14 Q3,42 3,31 V14 Q3,3 14,3 Z"
).toPath()

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
    val ink = if (dark) Color.White else Color(0xFF3C3C3C)
    Row(
        Modifier.fillMaxWidth().height(system.statusBarHeight.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            time, color = ink,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription =
                    "模拟状态：电量 ${system.batteryPercent}%，${if (system.charging) "充电中，" else ""}Wi-Fi ${if (system.wifi) "已连接" else "关闭"}，信号 ${system.mobileSignal} 格"
            }) {
            Canvas(Modifier.size(21.2.dp, 18.dp)) {
                for (i in 0..3) {
                    val left = floatArrayOf(2f, 16f, 30f, 44f)[i]
                    val top = floatArrayOf(28f, 21f, 11f, 2f)[i]
                    drawRoundRect(
                        ink.copy(alpha = if (i < system.mobileSignal) 1f else 0.2f),
                        topLeft = Offset((left * 0.4f).dp.toPx(), (top * 0.4f).dp.toPx()),
                        size = Size(3.2.dp.toPx(), ((41f - top) * 0.4f).dp.toPx()),
                        cornerRadius = CornerRadius(0.8.dp.toPx()),
                    )
                }
            }
            if (system.wifi) {
                Canvas(Modifier.size(23.6.dp, 18.dp)) {
                    scale(0.4.dp.toPx(), pivot = Offset.Zero) {
                        drawPath(statusWifiPath, ink)
                    }
                }
            }
            Canvas(Modifier.size(31.6.dp, 18.dp)) {
                val unit = 0.4.dp.toPx()
                val fill = if (system.charging) Color(0xFF34C759) else ink
                scale(unit, pivot = Offset.Zero) {
                    drawPath(statusBatteryPath, fill)
                }
                FontMgr.default.matchFamilyStyle("Arial", FontStyle.BOLD).use { typeface ->
                    Font(typeface, 14.sp.toPx()).use { font ->
                        Paint().use { paint ->
                            paint.color = (if (system.charging || dark) Color.Black else Color.White).toArgb()
                            val text = system.batteryPercent.toString()
                            // Center the visible glyphs in the body, excluding the terminal and font leading.
                            val bounds = font.measureText(text)
                            drawContext.canvas.nativeCanvas.drawString(
                                text,
                                38f * unit - (bounds.left + bounds.right) / 2,
                                22.5f * unit - (bounds.top + bounds.bottom) / 2,
                                font,
                                paint,
                            )
                        }
                    }
                }
            }
        }
    }
}
