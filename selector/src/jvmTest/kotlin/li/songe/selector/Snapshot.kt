package li.songe.selector

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.zip.ZipInputStream

@Serializable
data class Node(
    val id: Int,
    val pid: Int,
    val attr: Map<String, JsonPrimitive>,
) {
    @Transient
    var parent: Node? = null

    @Transient
    var children: MutableList<Node> = mutableListOf()

    override fun toString(): String {
        return id.toString()
    }
}

@Serializable
data class Snapshot(
    val nodes: List<Node>
)

private val assetsDir by lazy {
    File("../_assets").apply {
        if (!exists()) {
            mkdir()
        }
    }
}

private val json by lazy {
    Json {
        ignoreUnknownKeys = true
    }
}

private fun getNodeAttr(node: Node, name: String): Any? {
    if (name == "_id") return node.id
    if (name == "_pid") return node.pid
    if (name == "parent") return node.parent
    val value = node.attr[name] ?: return null
    if (value is JsonNull) return null
    if (value.isString) {
        return value.content
    }
    return value.intOrNull ?: value.booleanOrNull ?: value.content
}

private fun getNodeInvoke(target: Node, name: String, args: List<Any>): Any? {
    when (name) {
        "getChild" -> {
            val arg = args.getInt()
            return target.children.getOrNull(arg)
        }
    }
    return null
}

val transform by lazy {
    Transform<Node>(
        getAttr = { target, name ->
            when (target) {
                is QueryContext<*> -> when (name) {
                    "prev" -> target.prev
                    "current" -> target.current
                    else -> getNodeAttr(target.current as Node, name)
                }

                is Node -> getNodeAttr(target, name)
                is String -> getCharSequenceAttr(target, name)

                else -> null
            }
        },
        getInvoke = { target, name, args ->
            when (target) {
                is Boolean -> getBooleanInvoke(target, name, args)
                is Int -> getIntInvoke(target, name, args)
                is CharSequence -> getCharSequenceInvoke(target, name, args)
                is Node -> getNodeInvoke(target, name, args)
                is QueryContext<*> -> when (name) {
                    "getPrev" -> {
                        args.getInt().let { target.getPrev(it) }
                    }

                    else -> getNodeInvoke(target.current as Node, name, args)
                }

                else -> null
            }
        },
        getName = { node -> node.attr["name"]?.content },
        getChildren = { node -> node.children.asSequence() },
        getParent = { node -> node.parent }
    )
}

private val idToSnapshot by lazy {
    HashMap<String, Node>()
}

//val typeInfo by lazy { initDefaultTypeInfo(webField = true).globalType }

fun getSnapshotNode(url: String): Node {
    val githubAssetId = url.split('/').last()
    idToSnapshot[githubAssetId]?.let { return it }
    val file = assetsDir.resolve("$githubAssetId.json")
    if (!file.exists()) {
        val remoteUrl = URI.create("https://f.gkd.li/${githubAssetId}").toURL()
        remoteUrl.openStream().use { inputStream ->
            val zipInputStream = ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".json")) {
                    val outputStream = BufferedOutputStream(FileOutputStream(file))
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.close()
                    break
                }
                entry = zipInputStream.nextEntry
            }
            zipInputStream.closeEntry()
            zipInputStream.close()
        }
    }
    val nodes = json.decodeFromString<Snapshot>(file.readText()).nodes
    nodes.forEach { node ->
        node.parent = nodes.getOrNull(node.pid)
        node.parent?.apply {
            children.add(node)
        }
    }
    return nodes.first().apply {
        idToSnapshot[githubAssetId] = this
    }
}