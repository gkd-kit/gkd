package li.songe.gkd.priv

import li.songe.gkd.priv.shizuku.GkdShizukuExternalStartProvider
import priv.kit.ui.PrivilegeUiConfig

val gkdPrivilegeUiConfig: PrivilegeUiConfig by lazy {
    PrivilegeUiConfig(
        externalStartProviders = listOf(
            GkdShizukuExternalStartProvider,
        ),
    )
}
