package li.gkd.app.network.inspection

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.model.ExcludeData
import li.gkd.app.rule.CategorySetting
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.storage.AppStorageLayout
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionException
import li.gkd.app.subscription.SubscriptionFailureReason
import li.gkd.app.subscription.SubscriptionJson
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.db.SubscriptionConfigStore

/** Uses the existing application's isolated storage lifecycle and the actual HTTP listener. */
suspend fun assertSubscriptionRoutes(
    layout: AppStorageLayout,
    repository: SubscriptionRepository,
    response: (String, String, String) -> String,
) {
    fun fields(vararg values: Pair<String, JsonElement>) = JsonObject(values.toMap())
    val id = "subsId" to JsonPrimitive(-2)
    fun request(method: String, vararg values: Pair<String, JsonElement>): JsonElement =
        Json.parseToJsonElement(response(method, fields(*values).toString(), "ok"))
    fun error(method: String, code: String, vararg values: Pair<String, JsonElement>): JsonObject {
        val result = Json.parseToJsonElement(response(method, fields(*values).toString(), "error")).jsonObject
        assertEquals(code, result.getValue("code").jsonPrimitive.content)
        assertEquals(JsonNull, result["stackTrace"])
        assertTrue(result.getValue("message").jsonPrimitive.content.isNotBlank())
        return result
    }
    fun read() = request("getRules", id).jsonObject
    fun appTarget(key: Int) = fields(
        "groupType" to JsonPrimitive(2), "appId" to JsonPrimitive("test.alpha"), "groupKey" to JsonPrimitive(key),
    )
    fun globalTarget(key: Int, appId: JsonElement = JsonNull) = fields(
        "groupType" to JsonPrimitive(3), "appId" to appId, "groupKey" to JsonPrimitive(key),
    )

    repository.update(-2) { current ->
        RawSubscription.parse("""{id:-2,name:'HTTP rules',version:${current.version},categories:[{key:5,name:'HTTP',enable:false}]}""")
    }
    error("getRules", "INVALID_REQUEST", "subsId" to JsonPrimitive(13))
    // Unknown negative IDs must not acquire the built-in subscriptions' HTTP access.
    error("getRules", "INVALID_REQUEST", "subsId" to JsonPrimitive(-3))
    error("setRuleGroupSetting", "INVALID_REQUEST", id, "target" to appTarget(0))
    error("setCategorySetting", "INVALID_REQUEST", id, "categoryKey" to JsonPrimitive(5))

    val appended = request("appendRules", id,
        "groupType" to JsonPrimitive(2), "appId" to JsonPrimitive("test.alpha"),
        "source" to JsonPrimitive("""{id:'test.alpha',groups:[{key:0,name:'HTTP first',rules:[{matches:'[text="close"]'}]},{key:1,name:'HTTP second',rules:[{matches:'[text="skip"]'}]}]}"""),
    ).jsonArray
    assertEquals(listOf(0, 1), appended.map { it.jsonObject.getValue("key").jsonPrimitive.int })
    val collision = request("appendRules", id,
        "groupType" to JsonPrimitive(2), "appId" to JsonPrimitive("test.alpha"),
        "source" to JsonPrimitive("""{key:0,name:'HTTP third',rules:[{matches:'[text="third"]'}]}"""),
    ).jsonArray
    assertEquals(2, collision.single().jsonObject.getValue("key").jsonPrimitive.int)
    val group = "target" to appTarget(0)

    // Wrapped parser errors retain the precise field path and do not change the committed file.
    val file = layout.subscription.resolve("-2.json")
    val committed = file.readText()
    val invalid = error("replaceRuleGroup", "INVALID_SUBSCRIPTION", id, group,
        "source" to JsonPrimitive("""{name:'HTTP first',rules:[{actionMaximum:'bad'}]}"""),
    )
    assertEquals("$.rules[0].actionMaximum", invalid.getValue("path").jsonPrimitive.content)
    assertTrue(invalid.getValue("message").jsonPrimitive.content.contains("Expected"))
    val malformed = error("appendRules", "INVALID_SUBSCRIPTION", id,
        "groupType" to JsonPrimitive(2), "appId" to JsonPrimitive("test.alpha"), "source" to JsonPrimitive("{"),
    )
    assertEquals("$", malformed.getValue("path").jsonPrimitive.content)
    error("appendRules", "INVALID_SELECTOR", id,
        "groupType" to JsonPrimitive(2), "appId" to JsonPrimitive("test.alpha"),
        "source" to JsonPrimitive("""{name:'HTTP invalid',rules:[{matches:'['}]}"""),
    )
    error("appendRules", "RuleNameDuplicate", id,
        "groupType" to JsonPrimitive(2), "appId" to JsonPrimitive("test.alpha"),
        "source" to JsonPrimitive("""{name:'HTTP first',rules:[]}"""),
    )
    assertEquals(committed, file.readText())

    request("setSubscriptionEnabled", id, "enabled" to JsonPrimitive(true))
    for (value in listOf(JsonPrimitive(false), JsonPrimitive(true), JsonNull)) {
        val result = request("setSubscriptionAppSetting", id,
            "appId" to JsonPrimitive("test.alpha"), "enable" to value).jsonObject
        assertEquals(1, result.getValue("changed").jsonPrimitive.int)
        val config = read().getValue("configuration").jsonObject.getValue("appConfigs").jsonArray
        if (value == JsonNull) assertTrue(config.isEmpty())
        else assertEquals(value, config.single().jsonObject.getValue("enable"))
    }
    request("setRuleGroupSetting", id, group, "enable" to JsonPrimitive(false))
    val unchanged = request("setRuleGroupSetting", id, group, "enable" to JsonPrimitive(false)).jsonObject
    assertEquals(1, unchanged.getValue("unchanged").jsonPrimitive.int)
    // The shared execution path must retain the existing page commands' stale-edit checks.
    val pageSubscription = repository.awaitSnapshot().subscriptions.getValue(-2)
    val pageSwitch = RuleGroupConfigService.prepare(
        listOf(RuleSwitchTarget.AppGroup(-2, "test.alpha", 0)),
        listOf(pageSubscription), SubscriptionConfigStore.capture(),
    )
    request("setRuleGroupSetting", id, group, "enable" to JsonNull)
    assertEquals(SubscriptionFailureReason.RuleSwitchConflict,
        assertFailsWith<SubscriptionException> { RuleGroupConfigService.apply(pageSwitch, RuleSetting.Enabled) }.reason)
    request("setRuleGroupSetting", id, group, "enable" to JsonPrimitive(false))
    request("setCategorySetting", id, "categoryKey" to JsonPrimitive(5), "config" to fields("enable" to JsonPrimitive(true)))
    assertEquals(SubscriptionFailureReason.CategoryConfigConflict,
        assertFailsWith<SubscriptionException> {
            RuleGroupConfigService.setCategorySetting(pageSubscription, 5, CategorySetting.Disabled, CategorySetting.FollowSubscription)
        }.reason)
    for (value in listOf(JsonNull, fields("enable" to JsonNull), fields("enable" to JsonPrimitive(false)), fields("enable" to JsonPrimitive(true)), JsonNull)) {
        request("setCategorySetting", id, "categoryKey" to JsonPrimitive(5), "config" to value)
        val configs = read().getValue("configuration").jsonObject.getValue("categoryConfigs").jsonArray
        if (value == JsonNull) assertTrue(configs.isEmpty())
        else assertEquals(value.jsonObject.getValue("enable"), configs.single().jsonObject.getValue("enable"))
    }

    val source = "source" to JsonPrimitive("""{key:999,name:'HTTP updated',rules:[{matches:'[text="new"]'}]}""")
    assertTrue(request("replaceRuleGroup", id, group, source).jsonPrimitive.boolean)
    assertFalse(request("replaceRuleGroup", id, group, source).jsonPrimitive.boolean)
    assertEquals(JsonPrimitive(false), read().getValue("configuration").jsonObject
        .getValue("appGroupConfigs").jsonArray.single().jsonObject.getValue("enable"))
    val persisted = SubscriptionJson.json.decodeFromString<RawSubscription>(file.readText())
    assertEquals("HTTP updated", persisted.getAppGroups("test.alpha").first().name)
    assertEquals(0, persisted.getAppGroups("test.alpha").first().key)

    val global = request("appendRules", id, "groupType" to JsonPrimitive(3), "appId" to JsonNull,
        "source" to JsonPrimitive("""{key:0,name:'HTTP global',rules:[{matches:'[text="global"]'}]}"""),
    ).jsonArray
    assertEquals(0, global.single().jsonObject.getValue("key").jsonPrimitive.int)
    request("setRuleGroupSetting", id, "target" to globalTarget(0), "enable" to JsonPrimitive(false))
    request("setRuleGroupSetting", id, "target" to globalTarget(0, JsonPrimitive("test.alpha")), "enable" to JsonPrimitive(false))
    request("setRuleGroupSetting", id, "target" to globalTarget(0), "enable" to JsonNull)
    val config = SubscriptionConfigStore.capture().globalGroupConfigs.single { it.subsId == -2L }
    assertEquals(null, config.enable)
    assertEquals(true, ExcludeData.parse(config.exclude).appIds["test.alpha"])
    request("setRuleGroupSetting", id, "target" to globalTarget(0, JsonPrimitive("test.alpha")), "enable" to JsonNull)
    assertTrue(read().getValue("configuration").jsonObject.getValue("globalGroupConfigs").jsonArray.isEmpty())
    assertEquals(1, request("deleteRuleGroup", id, "target" to globalTarget(0)).jsonPrimitive.int)
    assertEquals(0, request("deleteRuleGroup", id, "target" to globalTarget(0)).jsonPrimitive.int)
    for (key in listOf(0, 1, 2)) assertEquals(1, request("deleteRuleGroup", id, "target" to appTarget(key)).jsonPrimitive.int)
    assertTrue(read().getValue("configuration").jsonObject.getValue("appGroupConfigs").jsonArray.isEmpty())
    assertTrue(repository.awaitSnapshot().subscriptions.getValue(-2).apps.isEmpty())
    error("replaceRuleGroup", "RuleMissing", id, group, source)
    error("setCategorySetting", "CategoryMissing", id, "categoryKey" to JsonPrimitive(999), "config" to JsonNull)

    // The older whole-memory-subscription route can load invalid groups; switching still uses the production policy.
    request("updateSubscription", "id" to JsonPrimitive(1), "name" to JsonPrimitive("fixture"), "version" to JsonPrimitive(1),
        "globalGroups" to Json.parseToJsonElement("""[{"key":8,"name":"invalid","rules":[{"matches":"["}]}]"""))
    val memoryId = "subsId" to JsonPrimitive(-1)
    val rejected = request("setRuleGroupSetting", memoryId, "target" to globalTarget(8), "enable" to JsonPrimitive(true)).jsonObject
    assertEquals(1, rejected.getValue("invalid").jsonPrimitive.int)
    assertEquals(0, rejected.getValue("changed").jsonPrimitive.int)
    for (subscriptionId in listOf(id, memoryId)) {
        val configuration = request("getRules", subscriptionId).jsonObject.getValue("configuration").jsonObject
        assertEquals(subscriptionId.second, configuration.getValue("subsItems").jsonArray.single().jsonObject.getValue("id"))
        for (key in listOf("appConfigs", "categoryConfigs", "appGroupConfigs", "globalGroupConfigs")) {
            assertTrue(configuration.getValue(key).jsonArray.all { it.jsonObject.getValue("subsId") == subscriptionId.second })
        }
    }
}
