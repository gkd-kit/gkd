package android.view;

import android.os.Build;

import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

@RemapType(MotionEvent.class)
public class MotionEventHidden {
    @RequiresApi(Build.VERSION_CODES.Q)
    public void setDisplayId(int displayId) {
    }
}
