package li.gkd.app.app

import li.gkd.app.model.AppInfo
import li.gkd.app.model.AppInventory
import java.text.Collator
import java.util.Locale

object AppCatalog {
    fun normalize(inventory: AppInventory): AppInventory {
        val primary = inventory.apps.associateBy { it.id }
        return inventory.copy(
            apps = primary.values.toList(),
            otherUsers = inventory.otherUsers.filter { it.id != inventory.userId }
                .sortedBy { it.id },
            othersApps = inventory.othersApps.sortedBy { it.userId }
                .filter { it.id !in primary }.distinctBy { it.id },
        )
    }

    fun visibleApps(apps: Collection<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance(Locale.CHINESE)
        return apps.filterNot { it.hidden }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }
}
