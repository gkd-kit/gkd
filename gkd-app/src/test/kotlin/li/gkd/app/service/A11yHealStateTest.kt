package li.gkd.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class A11yHealStateTest {

    private fun state(
        connected: Boolean = false,
        enabledInSettings: Boolean = true,
        a11yModeSelected: Boolean = true,
        canWriteSecureSettings: Boolean = true,
        userUnlocked: Boolean = true,
        activeServicesMissing: Boolean = false,
    ) = detectState(
        connected = connected,
        enabledInSettings = enabledInSettings,
        a11yModeSelected = a11yModeSelected,
        canWriteSecureSettings = canWriteSecureSettings,
        userUnlocked = userUnlocked,
        activeServicesMissing = activeServicesMissing,
    )

    /** 复现本缺陷: 设置里已启用但系统始终没有绑定服务, 必须进入可自愈状态 */
    @Test
    fun enabledWithoutConnectionIsFakeOnline() {
        assertEquals(
            A11yHealState.FakeOnline,
            state(enabledInSettings = true),
        )
    }

    /** 生效列表非空且缺少本服务, 说明系统确实没有持有服务, 仍属于可自愈的假在线 */
    @Test
    fun enabledButMissingFromActiveListIsFakeOnline() {
        assertEquals(
            A11yHealState.FakeOnline,
            state(enabledInSettings = true, activeServicesMissing = true),
        )
    }

    @Test
    fun connectedServiceIsAlwaysHealthy() {
        assertEquals(
            A11yHealState.Healthy,
            state(connected = true, enabledInSettings = false, a11yModeSelected = false),
        )
    }

    @Test
    fun lockedDeviceIsNotHealedYet() {
        assertEquals(
            A11yHealState.Unavailable,
            state(userUnlocked = false),
        )
    }

    @Test
    fun automationModeWithoutA11yServiceIsDisabled() {
        assertEquals(
            A11yHealState.Disabled,
            state(a11yModeSelected = false),
        )
    }

    /** 设置与生效列表都表明无障碍未启用时, 说明用户主动关闭, 不应擅自写回 */
    @Test
    fun disabledEverywhereIsDisabled() {
        assertEquals(
            A11yHealState.Disabled,
            state(enabledInSettings = false, activeServicesMissing = false),
        )
    }

    @Test
    fun missingWritePermissionIsUnavailable() {
        assertEquals(
            A11yHealState.Unavailable,
            state(canWriteSecureSettings = false),
        )
    }
}
