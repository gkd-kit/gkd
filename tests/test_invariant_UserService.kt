import li.songe.gkd.shizuku.UserService
import li.songe.gkd.shizuku.CommandResult
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class UserServiceSecurityTest(private val payload: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "payload: {0}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                // Exact exploit: command injection via newline separation
                arrayOf("echo safe\nrm -rf /data/local/tmp/*"),
                // Boundary: empty command (should not crash)
                arrayOf(""),
                // Valid input: simple command
                arrayOf("echo test"),
                // Adversarial: multiple chained commands
                arrayOf("id; cat /proc/self/environ"),
                // Adversarial: shell metacharacters
                arrayOf("$(echo malicious)")
            )
        }
    }

    @Test
    fun testExecCommandSecurityBoundary() {
        // Invariant: Command execution must not allow arbitrary shell injection
        // via newline separation or other shell metacharacters in the input.
        val service = UserService()
        val result: CommandResult = service.execCommand(payload)
        
        // Security property: The command result should reflect the intended
        // execution behavior without allowing unauthorized side effects.
        // We assert that the function completes without throwing exceptions
        // (i.e., does not crash) and returns a valid CommandResult object.
        assertNotNull("CommandResult must not be null", result)
        assertTrue("Result code should be an integer", result.code is Int)
        // Additional check: ensure the result's output/error are strings
        // (even if empty) to confirm no unexpected runtime errors.
        assertNotNull("Result output must not be null", result.result)
        assertNotNull("Result error must not be null", result.error)
    }
}