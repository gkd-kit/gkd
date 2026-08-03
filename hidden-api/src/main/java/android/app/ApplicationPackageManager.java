package android.app;

import android.content.pm.PackageInfo;

import java.util.List;

public class ApplicationPackageManager {
    public List<PackageInfo> getInstalledPackagesAsUser(int flags, int userId) {
        throw new RuntimeException();
    }

    public int getApplicationEnabledSetting(String packageName) {
        throw new RuntimeException();
    }
}
