package android.hardware.input;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.view.InputEvent;

import androidx.annotation.RequiresApi;

/**
 * @noinspection unused
 */
public interface IInputManager extends IInterface {
    abstract class Stub extends Binder implements IInputManager {
        public static IInputManager asInterface(IBinder binder) {
            throw new IllegalArgumentException("Stub!");
        }
    }

    boolean injectInputEvent(InputEvent ev, int mode);

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    boolean injectInputEventToTarget(InputEvent ev, int mode, int targetUid);
}
