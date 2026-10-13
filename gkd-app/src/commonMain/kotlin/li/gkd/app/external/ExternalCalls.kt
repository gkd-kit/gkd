package li.gkd.app.external

import li.gkd.app.storage.appStorage
import li.gkd.app.storage.writeTextAtomically
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object ExternalCalls {
    private val file by lazy { appStorage().privateStore.resolve("external-call-token.txt") }
    private val lock = Any()

    fun token(): String = synchronized(lock) {
        if (file.exists()) file.readText() else writeNewToken()
    }

    fun resetToken(): String = synchronized(lock) {
        writeNewToken()
    }

    fun accepts(token: String?): Boolean = synchronized(lock) {
        if (token.isNullOrEmpty() || !file.exists()) return@synchronized false
        MessageDigest.isEqual(
            file.readText().toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8),
        )
    }

    private fun writeNewToken(): String {
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ByteArray(12).also { SecureRandom().nextBytes(it) },
        )
        file.writeTextAtomically(token)
        return token
    }
}

expect fun externalCallComponent(): String

fun externalSnapshotCommand(token: String): String =
    "am start-foreground-service -n ${externalCallComponent()} " +
        "--es method capture-snapshot --es token $token"
