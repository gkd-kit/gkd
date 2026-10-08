package li.gkd.app.ui.image

import li.gkd.app.DesktopRuntime

actual fun appImageLoader() = DesktopRuntime.requireCurrent().imageLoader
