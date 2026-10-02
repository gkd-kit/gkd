package li.gkd.app.platform

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

actual fun requestPackageInstall(file: File): PlatformResult<Unit> = PlatformResult.Unsupported

actual fun openExternalUri(uri: String): PlatformResult<Unit> {
    if (!Desktop.isDesktopSupported()) return PlatformResult.Unsupported
    val desktop = Desktop.getDesktop()
    val target = URI(uri)
    if (target.scheme.equals("file", true)) {
        if (!desktop.isSupported(Desktop.Action.OPEN)) return PlatformResult.Unsupported
        desktop.open(File(target))
    } else {
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return PlatformResult.Unsupported
        desktop.browse(target)
    }
    return PlatformResult.Success(Unit)
}

actual fun writeClipboardText(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}
