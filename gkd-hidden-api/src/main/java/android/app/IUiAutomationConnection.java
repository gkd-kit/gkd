package android.app;

import android.accessibilityservice.IAccessibilityServiceClient;
import android.os.Binder;

public interface IUiAutomationConnection {
    abstract class Stub extends Binder implements IUiAutomationConnection {
    }

    void connect(IAccessibilityServiceClient client, int flags);

    void disconnect();

    void shutdown();
}
