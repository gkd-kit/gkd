package android.app;

import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

/**
 * @noinspection unused
 */
@RefineAs(TaskInfo.class)
@RequiresApi(Build.VERSION_CODES.Q)
public class TaskInfoHidden {
    public Configuration configuration;
}
