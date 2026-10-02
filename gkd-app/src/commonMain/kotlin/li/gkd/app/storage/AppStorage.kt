package li.gkd.app.storage

/** The host selects its process storage roots before any application state is read. */
expect fun appStorage(): AppStorageLayout
