package li.songe.gkd.composition

import android.content.Context
import android.content.Intent
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder

typealias StartCommandHook = (intent: Intent?, flags: Int, startId: Int) -> Unit

typealias onReceiveType = (Context?, Intent?) -> Unit

typealias  ConfigBubbleHook = ((BubbleBuilder) -> Unit) -> Unit