package li.songe.selector

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class TestNode(
    val id: Int,
    val pid: Int,
    val attr: Map<String, JsonPrimitive>,
) {
    @Transient
    var parent: TestNode? = null

    @Transient
    var children: MutableList<TestNode> = mutableListOf()

    override fun toString(): String {
        return id.toString()
    }
}

