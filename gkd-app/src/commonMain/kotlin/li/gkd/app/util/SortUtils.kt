package li.gkd.app.util

import java.text.Collator
import java.util.Locale

object SortUtils {
    val collator by lazy { Collator.getInstance(Locale.CHINESE)!! }
}
