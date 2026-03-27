package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 * @noinspection rawtypes, unused
 */
public class ParceledListSlice<T extends Parcelable> {
    public static final Parcelable.ClassLoaderCreator<ParceledListSlice> CREATOR = new Parcelable.ClassLoaderCreator<>() {
        public ParceledListSlice createFromParcel(Parcel var1) {
            throw new RuntimeException();
        }

        public ParceledListSlice createFromParcel(Parcel var1, ClassLoader var2) {
            throw new RuntimeException();
        }

        public ParceledListSlice[] newArray(int var1) {
            throw new RuntimeException();
        }
    };

    public List<T> getList() {
        throw new RuntimeException();
    }
}