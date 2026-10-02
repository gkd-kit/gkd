package li.gkd.app.storage

import li.gkd.app.DesktopStorage

actual fun appStorage(): AppStorageLayout = DesktopStorage.layout
