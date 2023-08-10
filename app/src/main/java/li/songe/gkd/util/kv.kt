package li.songe.gkd.util

import com.tencent.mmkv.MMKV


val kv by lazy { MMKV.mmkvWithID("kv", MMKV.MULTI_PROCESS_MODE)!! }
