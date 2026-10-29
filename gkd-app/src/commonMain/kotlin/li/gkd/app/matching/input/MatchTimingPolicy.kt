package li.gkd.app.matching.input

import kotlinx.serialization.Serializable

@Serializable
data class MatchTimingPolicy(
    val contentMergeMillis: Long = 100,
    val foregroundRetryMillis: Long = 100,
    val foregroundWaitMillis: Long = 3000,
    val rootWaitMillis: Long = 500,
    val followUpMillis: Long = 300,
    val followUpWindowMillis: Long = 3000,
    val pageSettleMillis: Long = 5000,
    val serviceHandoffMillis: Long = 1000,
) {
    init {
        require(contentMergeMillis in 0..5000)
        require(foregroundRetryMillis in 1..5000)
        require(foregroundWaitMillis in foregroundRetryMillis..30000)
        require(rootWaitMillis in 1..30000)
        require(followUpMillis in 1..30000)
        require(followUpWindowMillis in 0..60000)
        require(pageSettleMillis in 0..60000)
        require(serviceHandoffMillis in 0..10000)
    }
}
