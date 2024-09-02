package li.songe.gkd.composition

import android.content.Intent
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder

typealias StartCommandHook = (intent: Intent?, flags: Int, startId: Int) -> Unit

typealias  ConfigBubbleHook = ((BubbleBuilder) -> Unit) -> Unit