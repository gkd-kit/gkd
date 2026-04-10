package android.content;

import android.os.Build;

import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

/**
 * @noinspection unused
 */
@RemapType(Context.class)
public class ContextHidden {
    @RequiresApi(Build.VERSION_CODES.Q)
    public static String ACTIVITY_TASK_SERVICE;
}
