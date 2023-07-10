package li.songe.selector

import kotlinx.serialization.Serializable

@Serializable
data class TestSnapshot(
    val nodes: List<TestNode>
)
