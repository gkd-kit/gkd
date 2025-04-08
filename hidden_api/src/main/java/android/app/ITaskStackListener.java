package android.app;

import android.os.IBinder;

/**
 * @noinspection unused
 */
public interface ITaskStackListener {

    abstract class Stub extends android.os.Binder implements ITaskStackListener {
        public static ITaskStackListener asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    void onTaskStackChanged();
}