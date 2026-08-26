package android.hardware.input;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.view.InputEvent;

public interface IInputManager extends IInterface {
    abstract class Stub extends Binder implements IInputManager {
        public static IInputManager asInterface(IBinder binder) {
            throw new RuntimeException();
        }
    }

    boolean injectInputEvent(InputEvent ev, int mode);
}
