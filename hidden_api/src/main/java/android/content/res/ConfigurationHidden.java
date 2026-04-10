package android.content.res;

import android.app.WindowConfiguration;
import android.os.Build;

import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

/**
 * @noinspection unused
 */
@RemapType(Configuration.class)
public class ConfigurationHidden {
    @RequiresApi(Build.VERSION_CODES.P)
    public WindowConfiguration windowConfiguration;
}
