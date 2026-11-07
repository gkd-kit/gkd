package li.gkd.app.ui.component

import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun GkAndroidLanguageEnvironment(locale: Locale, content: @Composable () -> Unit) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localizedConfiguration =
        remember(configuration, locale) {
            Configuration(configuration).apply { setLocale(locale) }
        }
    val localizedContext =
        remember(context, localizedConfiguration) {
            context.createConfigurationContext(localizedConfiguration)
        }
    CompositionLocalProvider(
        LocalActivity provides activity,
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
    ) {
        GkLanguageEnvironment(locale, content)
    }
}
