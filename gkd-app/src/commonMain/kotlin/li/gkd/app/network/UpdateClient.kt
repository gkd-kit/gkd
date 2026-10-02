package li.gkd.app.network

import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import java.io.File

object UpdateClient {
    suspend fun fetch(url: String): NewVersion = NetworkClients.client.get(url).body()
    suspend fun download(url: String, file: File, size: Long, progress: (Float) -> Unit) {
        NetworkClients.client.get(url) { onDownload { received, _ -> if (size > 0) progress(received.toFloat() / size) } }
            .bodyAsChannel().copyAndClose(file.writeChannel())
    }
}
