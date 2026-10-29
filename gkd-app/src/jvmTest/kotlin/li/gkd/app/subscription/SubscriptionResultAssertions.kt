package li.gkd.app.subscription

import kotlin.test.assertIs

fun <T> SubscriptionEditResult<T>.expectValue(): T =
    assertIs<SubscriptionEditResult.Success<T>>(this).value

fun parseInput(source: String, defaultGroupKey: Int = 0): SubscriptionInputParser =
    SubscriptionInputParser.parse(source, defaultGroupKey).expectValue()

fun assertRejected(block: () -> Any): SubscriptionEditResult.Rejected =
    assertIs<SubscriptionEditResult.Rejected>(block())
