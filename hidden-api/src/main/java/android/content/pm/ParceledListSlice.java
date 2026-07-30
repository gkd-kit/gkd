package android.content.pm;

import android.os.Parcelable;

import java.util.List;

public class ParceledListSlice<T extends Parcelable> {
    public List<T> getList() {
        throw new RuntimeException();
    }
}
