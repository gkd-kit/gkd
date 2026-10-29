package li.gkd.app.service

import io.ktor.server.application.Application

/** Supplied by the final app's Debug source set; Release has no diagnostic transport. */
interface DebugServerDiagnostics : AutoCloseable {
    fun configure(application: Application)

    fun publish(port: Int)

    fun unpublish()
}
