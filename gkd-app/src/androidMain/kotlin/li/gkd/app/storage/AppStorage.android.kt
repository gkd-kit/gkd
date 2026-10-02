package li.gkd.app.storage

import li.gkd.app.util.AndroidStorage

actual fun appStorage(): AppStorageLayout = AndroidStorage.storage
