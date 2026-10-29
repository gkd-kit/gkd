package li.gkd.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import li.gkd.app.model.AppInfo

class SettingsAppIdsTest {
    @Test
    fun formattedListKeepsInstalledAppsFirstAndRoundTripsThroughBothDecoders() {
        val ids = setOf("a.missing", "z.installed", "android")
        val apps =
            mapOf(
                "z.installed" to AppInfo("z.installed", "示例应用", 1, null, false, 0, false, 0),
                "android" to AppInfo("android", "系统", 1, null, true, 0, false, 0),
            )
        val text = SettingsAppIds.format(ids, apps)
        assertEquals("android\n# 系统\n\nz.installed\n# 示例应用\n\na.missing\n\n", text)
        assertEquals(ids, SettingsAppIds.decode(text))
        assertEquals(ids, SettingsAppIds.decodeBackup(text))
        assertEquals("a.missing\nandroid\nz.installed", SettingsAppIds.encode(ids))
    }

    @Test
    fun editorAndBackupIgnoreInvalidLines() {
        val text = "# 应用\nandroid\n\ninvalid-id\nandroid"
        assertEquals(setOf("android"), SettingsAppIds.decode(text))
        assertEquals(setOf("android"), SettingsAppIds.decodeBackup(text))
        assertEquals("\n\n", SettingsAppIds.format(emptySet(), emptyMap()))
    }
}
