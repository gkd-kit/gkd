package li.gkd.app.permission

import li.gkd.app.resources.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStateTest {
    @Test
    fun uncheckedStateIsUnknownAndReadingDiagnosticsDoesNotRunChecks() {
        var reads = 0
        val permission = PermissionState("notification", Res.string.permission_notifications, {
            reads++
            false
        })
        assertNull(permission.lastCheck)
        assertEquals(0, reads)
        assertFalse(permission.updateAndGet())
        assertEquals(false, permission.lastCheck!!.granted)
        assertTrue(permission.lastCheck!!.checkedAt > 0)
        assertEquals(1, reads)
    }

    @Test
    fun failedCheckPreservesCauseAndPreviousBusinessStateThenRecovers() {
        var failure: Exception? = null
        val permission = PermissionState("notification", Res.string.permission_notifications, {
            failure?.let { throw it }
            true
        })
        assertTrue(permission.updateAndGet())
        val previous = permission.lastCheck
        val error = SecurityException("cannot read permission")
        failure = error
        assertSame(error, assertThrows(SecurityException::class.java) { permission.updateAndGet() })
        assertSame(previous, permission.lastCheck)
        assertTrue(permission.value)
        failure = null
        assertTrue(permission.updateAndGet())
        assertEquals(true, permission.lastCheck!!.granted)
    }
}
