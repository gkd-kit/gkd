package li.gkd.app.network

import io.ktor.utils.io.ClosedByteChannelException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkFailureLoggingTest {
    @Test
    fun channelCloseUsesTheTransportCauseWhenRecognized() {
        assertEquals(
            "network timeout",
            networkFailureSummary(
                ClosedByteChannelException(SocketTimeoutException("private URL")),
            ),
        )
        assertNull(networkFailureSummary(ClosedByteChannelException(IOException("unknown I/O"))))
    }

    @Test
    fun transportFailuresOmitSensitiveMessagesWhileDiskAndUnknownFailuresKeepDiagnostics() {
        assertEquals("network timeout", networkFailureSummary(SocketTimeoutException("secret URL")))
        assertEquals(
            "DNS resolution failed",
            networkFailureSummary(UnknownHostException("private host")),
        )
        assertEquals(
            "TLS SSLHandshakeException",
            networkFailureSummary(SSLHandshakeException("certificate detail")),
        )
        assertNull(networkFailureSummary(IOException("disk write failed")))
        assertNull(networkFailureSummary(IllegalStateException("unexpected state")))
    }
}
