package li.songe.gkd.util

import com.tencent.mmkv.MMKV

object Storage {

    val settings by lazy {
        kv.decodeParcelable(
            AppSettings.saveKey,
            AppSettings::class.java,
            null
        ) ?: AppSettings()
    }

    val kv: MMKV by lazy { MMKV.defaultMMKV() }
}