package li.songe.gkd.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import li.songe.gkd.util.Singleton


@Serializable
//@JsonClass(generateAdapter = true)
data class Subscription(
    @SerialName("appList")
    //@Json(name = "appList")
    val appList: List<App>,

    @SerialName("author")
    //@Json(name = "author")
    val author: String?=null,

    @SerialName("description")
    //@Json(name = "description")
    val description: String = "",

    @SerialName("version")
    //@Json(name = "version")
    val version: Int,

    @SerialName("url")
    //@Json(name = "url")
    var url: String = ""
) {


    companion object {
        fun parse(source: String) = Singleton.json.decodeFromString<Subscription>(source)
        fun stringify(source: Subscription) = Singleton.json.encodeToString(source)
    }

    @Serializable
    //@JsonClass(generateAdapter = true)
    data class App(
        @SerialName("groupList")
        //@Json(name = "groupList")
        val groupList: List<Group>,

        @SerialName("packageName")
        //@Json(name = "packageName")
        val packageName: String
    )

    @Serializable
    //@JsonClass(generateAdapter = true)
    data class Group(
        @SerialName("className")
        //@Json(name = "className")
        val className: String,

        @SerialName("description")
        //@Json(name = "description")
        val description: String?=null,

        @SerialName("key")
        //@Json(name = "key")
        val key: Int?=null,

        @SerialName("ruleList")
        //@Json(name = "ruleList")
        val ruleList: List<Rule>
    )

    @Serializable
    //@JsonClass(generateAdapter = true)
    data class Rule(
        @SerialName("className")
        //@Json(name = "className")
        val className: String?=null,

        @SerialName("description")
        //@Json(name = "description")
        val description: String?=null,

        @SerialName("selector")
        //@Json(name = "selector")
        val selector: String,

        @SerialName("prompt")
        //@Json(name = "prompt")
        val prompt: String?=null
    )
}