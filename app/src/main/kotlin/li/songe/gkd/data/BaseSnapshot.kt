package li.songe.gkd.data

interface BaseSnapshot {
    val id: Long

    val appId: String
    val activityId: String?

    val screenHeight: Int
    val screenWidth: Int
    val isLandscape: Boolean

}