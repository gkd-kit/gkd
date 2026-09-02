package li.gkd.selector

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

@Serializable(with = TestAttributesSerializer::class)
internal class TestAttributes(
    private val delegate: Map<String, Any?> = emptyMap(),
) : Map<String, Any?> by delegate

internal object TestAttributesSerializer : KSerializer<TestAttributes> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TestAttributes")

    override fun deserialize(decoder: Decoder): TestAttributes {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("TestAttributes only supports JSON")
        val element = jsonDecoder.decodeJsonElement()
        val jsonObject = element as? JsonObject
            ?: throw SerializationException("TestAttributes must be a JSON object")
        return TestAttributes(jsonObject.mapValues { (_, value) -> value.toTestAttribute() })
    }

    override fun serialize(encoder: Encoder, value: TestAttributes) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("TestAttributes only supports JSON")
        jsonEncoder.encodeJsonElement(
            JsonObject(value.mapValues { (_, attribute) -> attribute.toJsonElement() }),
        )
    }
}

@Serializable
internal class TestNode private constructor(
    val key: String,
    val name: String,
    val attributes: TestAttributes = TestAttributes(),
    val children: List<TestNode> = emptyList(),
) {
    internal constructor(
        key: String,
        name: String,
        attributes: Map<String, Any?> = emptyMap(),
        children: List<TestNode> = emptyList(),
    ) : this(
        key = key,
        name = name,
        attributes = TestAttributes(attributes),
        children = children,
    )

    @Transient
    var parent: TestNode? = null
        private set

    init {
        children.forEach { it.parent = this }
    }

    fun find(key: String): TestNode = sequence {
        val stack = mutableListOf(this@TestNode)
        while (stack.isNotEmpty()) {
            val node = stack.removeAt(stack.lastIndex)
            yield(node)
            for (index in node.children.lastIndex downTo 0) stack.add(node.children[index])
        }
    }.first { it.key == key }

    override fun toString(): String = key
}

internal object TestNodeAdapter : NodeAdapter<TestNode>() {
    var parentCallCount = 0

    fun resetCounters() {
        parentCallCount = 0
    }

    override fun getAttr(target: Any, name: String): Any? = when (target) {
        is TestNode -> target.attributes[name] ?: when (name) {
            "parent" -> target.parent
            "childCount" -> target.children.size
            "index" -> target.parent?.children?.indexOf(target) ?: 0
            "depth" -> generateSequence(target.parent) { it.parent }.count()
            else -> null
        }

        else -> null
    }

    override fun getInvoke(target: Any, name: String, args: List<Any>): Any? = when (target) {
        is TestNode -> when (name) {
            "getChild" -> target.children.getOrNull(args.first() as Int)
            "identity" -> args.firstOrNull()
            else -> null
        }

        else -> null
    }

    override fun getName(node: TestNode): String = node.name

    override fun getChildCount(node: TestNode): Int = node.children.size

    override fun getChild(node: TestNode, index: Int): TestNode? = node.children.getOrNull(index)

    override fun getParent(node: TestNode): TestNode? {
        parentCallCount++
        return node.parent
    }

    override fun getNodeKey(node: TestNode): Any = node

    override fun getFastQueryDescendants(
        node: TestNode,
        fastQueryList: List<FastQuery>,
    ): Sequence<TestNode> = getDescendants(node).filter { candidate ->
        fastQueryList.any { query ->
            query.acceptValue(candidate.attributes[query.attributeName])
        }
    }
}

private fun JsonElement.toTestAttribute(): Any? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        intOrNull != null -> intOrNull
        else -> throw SerializationException("Unsupported test attribute: $this")
    }

    else -> throw SerializationException("Test attributes must be JSON primitives: $this")
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is CharSequence -> JsonPrimitive(toString())
    else -> throw SerializationException("Unsupported test attribute: $this")
}
