package li.gkd.app.service

/**
 * 无障碍"假在线"的识别结果。
 *
 * 系统设置中的 enabled 记录与服务实际绑定状态并不一致: 开机阶段绑定失败或超时后,
 * Settings.Secure 中的条目会被保留, 但 AccessibilityManagerService 不会再重试,
 * 服务会长期停留在"设置里已开启但进程从未被拉起"的状态, 只能靠用户手动开关一次恢复。
 */
enum class A11yHealState {
    /** 服务真实连接, 无需处理 */
    Healthy,

    /** settings 已记录启用, 但系统从未回调 onServiceConnected */
    FakeOnline,

    /** settings 未记录启用, 属于用户未授权或已主动关闭 */
    Disabled,

    /** 当前不具备自愈条件(未连接 Shizuku/root, 或设备尚未解锁) */
    Unavailable,
}

/**
 * 纯判定逻辑, 不访问任何 Android API, 便于覆盖各分支的回归测试。
 */
fun detectState(
    connected: Boolean,
    enabledInSettings: Boolean,
    a11yModeSelected: Boolean,
    canWriteSecureSettings: Boolean,
    userUnlocked: Boolean,
    activeServicesMissing: Boolean,
): A11yHealState = when {
    // 进程内已经有真实回调过的服务实例, 无论设置状态如何都属于正常
    connected -> A11yHealState.Healthy
    // 未解锁时无障碍服务本身不可用, 重写设置也不会生效, 等到下一次重试
    !userUnlocked -> A11yHealState.Unavailable
    // 用户选择的是自动化模式, 无障碍服务本就不应该被拉起
    !a11yModeSelected -> A11yHealState.Disabled
    // 设置未启用且真实生效列表也确认缺失时, 说明用户主动关闭了无障碍
    !enabledInSettings && !activeServicesMissing -> A11yHealState.Disabled
    // 缺少写入安全设置的能力时无法自愈, 交由上层提示用户
    !canWriteSecureSettings -> A11yHealState.Unavailable
    else -> A11yHealState.FakeOnline
}
