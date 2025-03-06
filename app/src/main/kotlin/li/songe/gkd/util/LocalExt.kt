package li.songe.gkd.util

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import li.songe.gkd.MainViewModel


val LocalNavController = compositionLocalOf<NavHostController> {
    error("not found NavHostController")
}

val LocalMainViewModel = compositionLocalOf<MainViewModel> {
    error("not found MainViewModel")
}
