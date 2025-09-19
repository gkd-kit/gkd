package android.app;


import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

/**
 * @noinspection unused
 */
@RefineAs(AppOpsManager.class)
public class AppOpsManagerHidden {
    public static int OP_POST_NOTIFICATION;
    public static int OP_SYSTEM_ALERT_WINDOW;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static int OP_ACCESS_RESTRICTED_SETTINGS;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static int OP_FOREGROUND_SERVICE_SPECIAL_USE;

    public static String opToPublicName(int op) {
        throw new RuntimeException("Stub");
    }
}
