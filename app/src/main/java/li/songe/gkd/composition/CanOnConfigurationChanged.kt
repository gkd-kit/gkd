package li.songe.gkd.composition

import android.content.res.Configuration

interface CanOnConfigurationChanged {
    fun onConfigurationChanged(f: (newConfig: Configuration) -> Unit):Boolean
}