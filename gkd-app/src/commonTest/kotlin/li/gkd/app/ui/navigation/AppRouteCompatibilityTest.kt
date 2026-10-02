package li.gkd.app.ui.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRouteCompatibilityTest {
    // Android saved navigation payloads used the original package names before KMP.
    @Test
    fun restoresExistingParameterizedAndroidRoute() {
        val route =
            Json.decodeFromString<AppRoute>("""{"type":"li.gkd.app.feature.subscription.UpsertRuleGroupRoute","subsId":-2,"groupKey":7,"appId":"com.example.app","forward":true}""")
        assertEquals(UpsertRuleGroupRoute(-2, 7, "com.example.app", true), route)
        assertEquals(route, Json.decodeFromString<AppRoute>(Json.encodeToString(route)))
    }

    @Test
    fun restoresOlderRouteWithOmittedOptionalArguments() {
        val route =
            Json.decodeFromString<AppRoute>("""{"type":"li.gkd.app.feature.log.ActionLogRoute"}""")
        assertEquals(ActionLogRoute(), route)
    }
}
