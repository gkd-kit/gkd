package li.songe.gkd.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import li.songe.gkd.util.Singleton


@Serializable
data class Subscription(
    @SerialName("name") val name: String? = null,
    @SerialName("version") val version: Int,
    @SerialName("author") val author: String? = null,
    @SerialName("updateUrl") val updateUrl: String? = null,
    @SerialName("appList") val appList: List<App>,
) {

    companion object {
        fun parse(source: String) = Singleton.json.decodeFromString<Subscription>(source)
        fun parse5(source: String): Subscription {
            return Singleton.json.decodeFromString(
                Singleton.json5.load(source).toJson()
            )
        }
        fun stringify(source: Subscription) = Singleton.json.encodeToString(source)
    }

    @Serializable
    data class App(
        @SerialName("id") val id: String,
        @SerialName("groupList") val groupList: List<Group>,
    )

    @Serializable
    data class Group(
        @SerialName("key") val key: Int? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("activityId") val activityId: String,
        @SerialName("cd") val cd: Int? = null,
        @SerialName("ruleList") val ruleList: List<Rule>,
    )

    @Serializable
    data class Rule(
        @SerialName("key") val key: Int? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("activityId") val activityId: String? = null,
        @SerialName("cd") val cd: Int? = null,
        @SerialName("prompt") val prompt: String? = null,
        @SerialName("match") val match: String,
        @SerialName("action") val action: Action? = null,
        @SerialName("ordered") val ordered: Boolean = false,
    )

    @Serializable
    data class Action(
        @SerialName("type") val type: String,
        @SerialName("target") val target: String,
        @SerialName("position") val position: String? = null,
    )
}