package android.content.res;

import android.app.WindowConfiguration;
import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

/**
 * @noinspection unused
 */
@RefineAs(Configuration.class)
public class ConfigurationHidden {
    @RequiresApi(Build.VERSION_CODES.P)
    public WindowConfiguration windowConfiguration;
}
