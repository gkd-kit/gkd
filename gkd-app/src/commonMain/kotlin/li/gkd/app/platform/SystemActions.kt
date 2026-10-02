package li.gkd.app.platform

import java.io.File

/** Requested means the system installer was opened, not that installation completed. */
expect fun requestPackageInstall(file: File): PlatformResult<Unit>

/** Success means the open request was handed to the system. */
expect fun openExternalUri(uri: String): PlatformResult<Unit>
expect fun writeClipboardText(text: String)
