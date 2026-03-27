package android.content.pm;

import dev.rikka.tools.refine.RefineAs;

/**
 * @noinspection unused
 */
@RefineAs(IPackageManager.class)
public interface IPackageManagerCompat {
    // android 17+
    PackageInfoList getInstalledPackages(long flags, int userId);
}
