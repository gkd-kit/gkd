package li.songe.gkd.subs

import kotlinx.serialization.decodeFromString
import li.songe.gkd.subs.data.GkdSubs
import li.songe.gkd.util.Singleton.json
import li.songe.gkd.util.Singleton.json5

object SubsParser : (String) -> Unit {
    override fun invoke(p0: String) {
        val gkdSubs: GkdSubs = json.decodeFromString(json5.load(p0).toJson(false, false))
    }
}