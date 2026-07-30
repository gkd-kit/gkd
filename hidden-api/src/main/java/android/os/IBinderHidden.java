package android.os;

import li.songe.remap.RemapStub;
import li.songe.remap.RemapType;

@RemapType(IBinder.class)
public interface IBinderHidden {
    int SHELL_COMMAND_TRANSACTION = RemapStub.value();
}
