package li.gkd.app.priv

import android.app.Application
import android.content.pm.PackageManager
import java.io.IOException
import li.gkd.app.permission.AndroidPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import priv.kit.core.Privilege

@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    sdk = [26, 33],
    manifest = Config.NONE,
    shadows = [InputInjectionPermissionTest.PrivilegeShadow::class],
    instrumentedPackages = ["priv.kit.core"],
)
class InputInjectionPermissionTest {
    @Implements(value = Privilege::class, isInAndroidSdk = false)
    class PrivilegeShadow {
        var denied = emptySet<String>()
        var failure: Exception? = null
        val queries = mutableListOf<String>()

        @Implementation
        fun checkServerPermission(permission: String): Int {
            queries += permission
            failure?.let { throw it }
            return if (permission in denied) PackageManager.PERMISSION_DENIED
            else PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Input denial and later recovery must be read from the server instead of a cached UI state.
     */
    @Test
    fun inputDenialAndRecoveryAreCheckedIndependently() {
        val server = Shadow.extract<PrivilegeShadow>(Privilege)
        val firstQuery = server.queries.size
        server.denied = setOf(AndroidPermissions.INJECT_EVENTS)
        assertFalse(hasInputInjectionPermission())
        server.denied = emptySet()
        assertTrue(hasInputInjectionPermission())
        assertEquals(
            listOf(AndroidPermissions.INJECT_EVENTS, AndroidPermissions.INJECT_EVENTS),
            server.queries.drop(firstQuery),
        )
    }

    /** An otherwise restricted server can still supply input for the no-gesture service. */
    @Test
    fun otherServerRestrictionsDoNotDisableInputInjection() {
        val server = Shadow.extract<PrivilegeShadow>(Privilege)
        server.denied =
            setOf(
                AndroidPermissions.GRANT_RUNTIME_PERMISSIONS,
                AndroidPermissions.WRITE_SECURE_SETTINGS,
                AndroidPermissions.MANAGE_APP_OPS_MODES,
                AndroidPermissions.UPDATE_APP_OPS_STATS,
            )
        val capabilities = queryPrivilegeCapabilities()
        assertTrue(capabilities.restricted)
        assertTrue(capabilities.injectEvents)
        server.queries.clear()
        assertTrue(hasInputInjectionPermission())
        assertEquals(listOf(AndroidPermissions.INJECT_EVENTS), server.queries)
    }

    /** A failed query must remain an error, rather than turning into permission denial. */
    @Test
    fun queryFailurePropagatesAndDoesNotPreventRecovery() {
        val server = Shadow.extract<PrivilegeShadow>(Privilege)
        val failure = IOException("permission query failed")
        server.failure = failure
        assertSame(failure, assertThrows(IOException::class.java) { hasInputInjectionPermission() })
        server.failure = null
        assertTrue(hasInputInjectionPermission())
    }
}
