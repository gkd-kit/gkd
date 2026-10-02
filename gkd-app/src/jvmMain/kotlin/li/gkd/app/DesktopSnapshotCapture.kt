package li.gkd.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.snapshot.SnapshotAutoExportResult
import li.gkd.app.snapshot.SnapshotCaptureInput
import li.gkd.app.snapshot.SnapshotScreenshotStatus
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.util.Base64

/** Explicit development input. The source id is ignored; capture assigns a new timestamp. */
@Serializable
data class DesktopSnapshotCaptureRequest(
    val source: ComplexSnapshot,
    val screenshotBase64: String,
    val autoExport: Boolean = false,
)

@Serializable
data class DesktopSnapshotCaptureResult(
    val snapshot: ComplexSnapshot,
    val exportResult: String,
)

suspend fun DesktopRuntime.replaySnapshot(request: DesktopSnapshotCaptureRequest): DesktopSnapshotCaptureResult {
    require(DesktopStorage.isolated) { "Snapshot capture replay requires --test" }
    val result = li.gkd.app.snapshot.SnapshotCaptureRepository.capture(request.autoExport) {
        withContext(Dispatchers.Default) {
            val source = request.source
            val screenshot =
                Image.makeFromEncoded(Base64.getDecoder().decode(request.screenshotBase64))
                    .use { image ->
                        require(image.width == source.screenWidth && image.height == source.screenHeight)
                        image.encodeToData(EncodedImageFormat.WEBP, 85)?.use { it.bytes }
                            ?: error("Cannot encode replay screenshot")
                    }
            SnapshotCaptureInput(
                source.appId, source.activityId, source.screenHeight, source.screenWidth,
                source.isLandscape, source.nodes, source.appInfo, source.gkdAppInfo, source.device,
                screenshot, SnapshotScreenshotStatus.Captured,
            )
        }
    }
    return DesktopSnapshotCaptureResult(
        result.snapshot, when (val exported = result.autoExport) {
            SnapshotAutoExportResult.Disabled -> "disabled"
            SnapshotAutoExportResult.PermissionDenied -> "permissionDenied"
            is SnapshotAutoExportResult.Completed -> when (exported.result) {
                is li.gkd.app.platform.PlatformResult.Success -> "saved"
                li.gkd.app.platform.PlatformResult.Unsupported -> "unsupported"
            }

            is SnapshotAutoExportResult.Failed -> "failed: ${exported.cause.message}"
        }
    )
}
