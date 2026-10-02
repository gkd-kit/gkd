package li.gkd.app.platform.lifecycle

import li.gkd.app.util.LogUtils
import li.songe.codeorigin.CallSite

class ResourceSlot<T : AutoCloseable> : AutoCloseable {
    private var resource: T? = null
    private var resourceLoc = ""
    private var closed = false

    @Synchronized
    fun replace(
        newResource: T?,
        @CallSite loc: String = "",
    ): T? {
        check(!closed) { "ResourceSlot is already closed" }
        if (resource === newResource) return newResource
        val oldResource = resource
        val oldResourceLoc = resourceLoc
        resource = newResource
        resourceLoc = if (newResource == null) "" else loc
        oldResource?.closeSafely(oldResourceLoc)
        return newResource
    }

    @Synchronized
    fun replace(
        @CallSite loc: String = "",
        createResource: () -> T,
    ): T {
        check(!closed) { "ResourceSlot is already closed" }
        val oldResource = resource
        val oldResourceLoc = resourceLoc
        resource = null
        resourceLoc = ""
        oldResource?.closeSafely(oldResourceLoc)
        return createResource().also {
            resource = it
            resourceLoc = loc
        }
    }

    @Synchronized
    fun get(): T? = resource

    @Synchronized
    fun clear() {
        if (closed) return
        val oldResource = resource
        val oldResourceLoc = resourceLoc
        resource = null
        resourceLoc = ""
        oldResource?.closeSafely(oldResourceLoc)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        val oldResource = resource
        val oldResourceLoc = resourceLoc
        resource = null
        resourceLoc = ""
        oldResource?.closeSafely(oldResourceLoc)
    }

    private fun T.closeSafely(loc: String) {
        runCatching(::close).onFailure { LogUtils.d(it, loc = loc) }
    }
}
