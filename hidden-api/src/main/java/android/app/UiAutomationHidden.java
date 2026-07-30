package android.app;

import android.os.Looper;

import li.songe.remap.RemapType;

@RemapType(UiAutomation.class)
public class UiAutomationHidden {

    public UiAutomationHidden(Looper looper, IUiAutomationConnection connection) {
        throw new RuntimeException();
    }

    public void connect(int flag) {
    }

    public void disconnect() {
    }
}
