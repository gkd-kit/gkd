package li.songe.gkd.composition

import android.content.Context
import android.content.Intent
import com.torrydo.floatingbubbleview.FloatingBubble

typealias StartCommandHook = (intent: Intent?, flags: Int, startId: Int) -> Unit

typealias onReceiveType = (Context?, Intent?) -> Unit

typealias  SetupBubbleHook = (FloatingBubble.Action, (FloatingBubble.Builder) -> Unit) -> Unit