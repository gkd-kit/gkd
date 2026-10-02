package li.gkd.app

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/** Attaches native resources without starting services or the production application lifecycle. */
class ResourceTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Avoid App.attachBaseContext/onCreate: these initialize privileged Android services.
        ReflectionHelpers.setField(App(), "mBase", this)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = ResourceTestApplication::class, sdk = [28])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
abstract class AndroidResourcesTest
