package android.app;

import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

/**
 * @noinspection unused
 */
@RemapType(TaskInfo.class)
@RequiresApi(Build.VERSION_CODES.Q)
public class TaskInfoHidden {
    public Configuration configuration;
}
