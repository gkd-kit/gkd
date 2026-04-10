package android.view;

import android.os.Build;

import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

/**
 * @noinspection unused
 */
@RemapType(KeyEvent.class)
public class KeyEventHidden {
    @RequiresApi(Build.VERSION_CODES.Q)
    public void setDisplayId(int displayId) {
        throw new RuntimeException();
    }
}
