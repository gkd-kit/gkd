package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 * @noinspection rawtypes, unused
 */
public class ParceledListSlice<T extends Parcelable> {
    public static final Parcelable.ClassLoaderCreator<ParceledListSlice> CREATOR = new Parcelable.ClassLoaderCreator<ParceledListSlice>() {
        public ParceledListSlice createFromParcel(Parcel var1) {
            throw new UnsupportedOperationException();
        }

        public ParceledListSlice createFromParcel(Parcel var1, ClassLoader var2) {
            throw new UnsupportedOperationException();
        }

        public ParceledListSlice[] newArray(int var1) {
            throw new UnsupportedOperationException();
        }
    };

    public List<T> getList() {
        throw new UnsupportedOperationException();
    }
}