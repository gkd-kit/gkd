package li.songe.gkd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import li.songe.gkd.ui.BottomNavigationBar
import li.songe.gkd.ui.NavHostContainer
import li.songe.gkd.ui.theme.AdCloserTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdCloserTheme {
                Surface(color = MaterialTheme.colors.background) {
                    Surface(color = Color.White) {
                        val navController = rememberNavController()
                        Scaffold(
                            bottomBar = {
                                BottomNavigationBar(navController = navController)
                            }, content = { padding ->
                                NavHostContainer(navController = navController, padding = padding)
                            }
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
            }
        }
    }
}