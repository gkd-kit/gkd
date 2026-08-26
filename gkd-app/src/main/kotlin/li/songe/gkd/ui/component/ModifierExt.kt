package li.songe.gkd.ui.component

import androidx.compose.ui.Modifier

inline fun Modifier.runIf(
    enabled: Boolean,
    block: Modifier.() -> Modifier
) = run {
    if (enabled) {
        block()
    } else {
        this
    }
}