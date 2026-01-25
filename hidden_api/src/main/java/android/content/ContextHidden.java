package android.content;

import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

/**
 * @noinspection unused
 */
@RefineAs(Context.class)
public class ContextHidden {
    @RequiresApi(Build.VERSION_CODES.Q)
    public static String ACTIVITY_TASK_SERVICE;
}
