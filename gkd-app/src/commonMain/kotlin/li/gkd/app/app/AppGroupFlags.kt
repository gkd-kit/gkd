package li.gkd.app.app

/** Bit values are part of the persisted settings contract. */
object AppGroupFlags {
    const val System = 1 shl 0
    const val User = 1 shl 1
    const val Uninstalled = 1 shl 2
    const val Installed = System or User
}
