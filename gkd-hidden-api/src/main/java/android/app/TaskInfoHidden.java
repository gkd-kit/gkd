package android.app;

import android.os.Build;

import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

@RemapType(TaskInfo.class)
@RequiresApi(Build.VERSION_CODES.Q)
public class TaskInfoHidden {
    public int displayId;
}
