package li.songe.gkd.util

import com.blankj.utilcode.util.ProcessUtils


val isMainProcess by lazy { ProcessUtils.isMainProcess() }

val currentProcessName by lazy { ProcessUtils.getCurrentProcessName() }
