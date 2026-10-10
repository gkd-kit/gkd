package li.gkd.app.subscription

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RawSubscriptionParseTest {
    private fun parse(body: String) = RawSubscription.parse("""{id:1,name:'Test',version:1,$body}""")

    private fun assertPath(body: String, path: String, detail: String? = null) {
        val error = assertThrows(SubscriptionParseException::class.java) { parse(body) }
        assertEquals(path, error.path)
        if (detail != null) assertEquals(detail, error.detail)
        assertEquals("${error.path}: ${error.detail}", error.message)
    }

    @Test
    fun reportsMissingFieldsAndContainerTypes() {
        val missing = assertThrows(SubscriptionParseException::class.java) { RawSubscription.parse("{}") }
        assertEquals("$.id", missing.path)
        assertEquals("Required field is missing", missing.detail)
        assertPath("apps: {}", "$.apps", "Expected an array")
        assertPath("apps: [null]", "$.apps[0]", "Expected an object")
        assertPath("apps: [{}]", "$.apps[0].id", "Required field is missing")
        assertPath("apps: [{id:123}]", "$.apps[0].id", "Expected a string")
        assertPath("categories: [{key:0}]", "$.categories[0].name", "Required field is missing")
        val root = assertThrows(SubscriptionParseException::class.java) { RawSubscription.parse("[]") }
        assertEquals("$", root.path)
    }

    @Test
    fun tracksNestedRuleFieldsArrayItemsAndMatchers() {
        fun rule(value: String) = "apps:[{id:'app',groups:[{key:0,name:'Group',rules:[$value]}]}]"
        assertPath(rule("{actionMaximum:'bad'}"), "$.apps[0].groups[0].rules[0].actionMaximum", "Expected an integer")
        assertPath(rule("{actionMaximum:2147483648}"), "$.apps[0].groups[0].rules[0].actionMaximum", "Expected an integer")
        assertPath(rule("{fastQuery:'bad'}"), "$.apps[0].groups[0].rules[0].fastQuery", "Expected a boolean")
        assertPath(rule("{matches:['good',{}]}"), "$.apps[0].groups[0].rules[0].matches[1]")
        assertPath(rule("{preKeys:[1,'bad']}"), "$.apps[0].groups[0].rules[0].preKeys[1]")
        assertPath(rule("{versionCode:{include:[1,{}]}}"), "$.apps[0].groups[0].rules[0].versionCode.include[1]")
        assertPath(rule("{versionName:{exclude:['a',null]}}"), "$.apps[0].groups[0].rules[0].versionName.exclude[1]")
        assertPath(rule("{versionCodes:[1,'bad']}"), "$.apps[0].groups[0].rules[0].versionCodes[1]")
        // A completed matcher scope must not leak into the later position field's path.
        assertPath(rule("{versionCode:{minimum:1},position:{x:[],y:0}}"), "$.apps[0].groups[0].rules[0].position.x")
        assertPath(rule("{swipeArg:{start:{x:0,y:[]},duration:1}}"), "$.apps[0].groups[0].rules[0].swipeArg.start.y")
        assertPath(rule("{swipeArg:{start:{x:0,y:0}}}"), "$.apps[0].groups[0].rules[0].swipeArg.duration")
    }

    @Test
    fun globalScopesAndShorthandReportActualInputPaths() {
        assertPath("globalGroups:[{key:0,name:'G',apps:[{}]}]", "$.globalGroups[0].apps[0].id")
        assertPath("globalGroups:[{key:0,name:'G',rules:[{apps:[{}]}]}]", "$.globalGroups[0].rules[0].apps[0].id")
        assertPath("globalGroups:[{key:0,name:'G',rules:{}}]", "$.globalGroups[0].rules", "Expected an array")
        assertPath("apps:[{id:'app',groups:{key:0,name:'G',rules:{preKeys:['bad']}}}]", "$.apps[0].groups.rules.preKeys[0]")
        assertPath("apps:[{id:'app',groups:[{key:0,name:'G',rules:[['good',{}]]}]}]", "$.apps[0].groups[0].rules[0][1]")
        val group = SubscriptionJson.json.parseToJsonElement("""{"key":0,"name":"G","rules":[{"actionMaximum":"bad"}]}""")
        val standalone = assertThrows(SubscriptionParseException::class.java) {
            RawSubscription.parseAppGroup(group.jsonObject)
        }
        assertEquals("$.rules[0].actionMaximum", standalone.path)
    }

    @Test
    fun indicesReferToInputBeforeFilteringAndDeduplication() {
        assertPath(
            "apps:[{id:'empty'},{id:'app',groups:[{key:0,name:'A'},{key:0,name:'B'},{key:0,name:'C',actionMaximum:'bad'}]}]",
            "$.apps[1].groups[2].actionMaximum",
        )
        assertPath("categories:[{key:0,name:''},{key:0,name:'A'},{key:0,name:123}]", "$.categories[2].name")
    }

    @Test
    fun preservesJson5ShorthandLegacyAliasesDefaultsAndDeduplication() {
        val subscription = parse("""
            // Existing input conveniences remain valid.
            apps:[{id:'empty'},{id:'app',groups:{
                key:'7',name:'Group',cd:'10',delay:'20',versionCodes:[1,'2'],
                rules:['[text="x"]',
                    {key:'1',matches:['x',1,true],position:{left:1,top:'2'},
                     swipeArg:{start:{x:0,y:1},duration:'500'}},
                    {key:1,matches:'duplicate'},
                ],
            }}],
            categories:[{key:0,name:''},{key:1,name:'One'},{key:1,name:'Duplicate'}],
        """.trimIndent())
        assertEquals(listOf("app"), subscription.apps.map { it.id })
        assertEquals(listOf("One"), subscription.categories.map { it.name })
        val group = subscription.apps.single().groups.single()
        assertEquals(7, group.key)
        assertEquals(10L, group.actionCd)
        assertEquals(20L, group.actionDelay)
        assertEquals(listOf(1, 2), group.versionCode?.include)
        assertEquals(2, group.rules.size)
        assertEquals(listOf("[text=\"x\"]"), group.rules[0].matches)
        assertEquals(listOf("x", "1", "true"), group.rules[1].matches)
        assertEquals("1", group.rules[1].position?.left)
        assertEquals(500L, group.rules[1].swipeArg?.duration)
        assertEquals(emptyList<RawSubscription.RawGlobalGroup>(), subscription.globalGroups)
        // Saved JSON must still load through the same parser as JSON5 input.
        val restored = RawSubscription.parse(SubscriptionJson.json.encodeToString(subscription), json5 = false)
        assertEquals(group.rules[1], restored.apps.single().groups.single().rules[1])
    }

    @Test
    fun syntaxFailuresKeepTheirCauseAndDoNotContaminateLaterParses() {
        for (json5 in listOf(false, true)) {
            val error = assertThrows(SubscriptionParseException::class.java) { RawSubscription.parse("{", json5) }
            assertEquals("$", error.path)
            assertNotNull(error.cause)
        }
        assertPath("apps:[{id:'app',groups:[{name:'Missing key'}]}]", "$.apps[0].groups[0].key")
        assertEquals(1L, parse("apps:[]").id)
        assertPath("categories:[{}]", "$.categories[0].key")
    }
}
