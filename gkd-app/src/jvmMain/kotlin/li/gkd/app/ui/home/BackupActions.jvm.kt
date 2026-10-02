package li.gkd.app.ui.home

import li.gkd.app.ui.navigation.AppWindow

actual fun AppWindow.importAppBackup() {
    importBackup()
}

actual fun AppWindow.shareAppBackup() {
    exportBackup(share = true)
}

actual fun AppWindow.saveAppBackup() {
    exportBackup(save = true)
}
