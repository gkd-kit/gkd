package com.android.internal.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * @noinspection unused
 */
public interface IAppOpsService extends IInterface {
    abstract class Stub extends Binder implements IAppOpsService {
        public static IAppOpsService asInterface(IBinder obj) {
            throw new RuntimeException();
        }
    }

    int checkOperation(int code, int uid, String packageName);

    void setMode(int code, int uid, String packageName, int mode);
}
