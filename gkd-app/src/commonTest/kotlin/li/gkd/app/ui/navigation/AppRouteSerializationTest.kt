package li.gkd.app.ui.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRouteSerializationTest {
    // Desktop scenario requests accept serialized routes and optional arguments.
    @Test
    fun decodesParameterizedScenarioRoute() {
        val route =
            Json.decodeFromString<AppRoute>("""{"type":"li.gkd.app.ui.navigation.UpsertRuleGroupRoute","subsId":-2,"groupKey":7,"appId":"com.example.app","forward":true}""")
        assertEquals(UpsertRuleGroupRoute(-2, 7, "com.example.app", true), route)
        assertEquals(route, Json.decodeFromString<AppRoute>(Json.encodeToString(route)))
    }

    @Test
    fun decodesScenarioRouteWithOmittedOptionalArguments() {
        val route =
            Json.decodeFromString<AppRoute>("""{"type":"li.gkd.app.ui.navigation.ActionLogRoute"}""")
        assertEquals(ActionLogRoute(), route)
    }
}
