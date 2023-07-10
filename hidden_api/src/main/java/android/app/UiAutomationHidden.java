package android.app;

import android.os.Looper;
import android.os.ParcelFileDescriptor;

import dev.rikka.tools.refine.RefineAs;

@SuppressWarnings("unused")
@RefineAs(UiAutomation.class)
public class UiAutomationHidden {

    public UiAutomationHidden(Looper looper, IUiAutomationConnection connection) {
        throw new RuntimeException("Stub!");
    }

    public void connect() {
        throw new RuntimeException("Stub!");
    }

    public void connect(int flag) {
        throw new RuntimeException("Stub!");
    }

    public void disconnect() {
        throw new RuntimeException("Stub!");
    }

    public ParcelFileDescriptor[] executeShellCommandRwe(String command) {
        throw new RuntimeException("Stub!");
    }
}
