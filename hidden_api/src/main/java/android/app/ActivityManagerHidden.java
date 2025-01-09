package android.app;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(ActivityManager.class)
public class ActivityManagerHidden {

    public static boolean isHighEndGfx() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Represents a task snapshot.
     */
    public static class TaskSnapshot implements Parcelable {

        protected TaskSnapshot(Parcel in) {
            //Stub
        }

        public static final Creator<TaskSnapshot> CREATOR = new Creator<TaskSnapshot>() {
            @Override
            public TaskSnapshot createFromParcel(Parcel in) {
                return new TaskSnapshot(in);
            }

            @Override
            public TaskSnapshot[] newArray(int size) {
                return new TaskSnapshot[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel parcel, int i) {
        }
    }

}