package li.songe.gkd.utils

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController


val LocalNavController =
    compositionLocalOf<NavHostController> { error("not found DestinationsNavigator") }
