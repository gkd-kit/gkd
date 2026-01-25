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
    @RequiresApi(Build.VERSION_CODES.P)
    public static String OPSTR_POST_NOTIFICATION;

    public static int OP_SYSTEM_ALERT_WINDOW;
    public static String OPSTR_SYSTEM_ALERT_WINDOW;

    @RequiresApi(Build.VERSION_CODES.Q)
    public static int OP_ACCESS_ACCESSIBILITY;

    @RequiresApi(Build.VERSION_CODES.Q)
    public static String OPSTR_ACCESS_ACCESSIBILITY;

    // https://diff.songe.li/?ref=AppOpsManager.OP_CREATE_ACCESSIBILITY_OVERLAY
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static int OP_CREATE_ACCESSIBILITY_OVERLAY;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static String OPSTR_CREATE_ACCESSIBILITY_OVERLAY;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static int OP_ACCESS_RESTRICTED_SETTINGS;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static String OPSTR_ACCESS_RESTRICTED_SETTINGS;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static int OP_FOREGROUND_SERVICE_SPECIAL_USE;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static String OPSTR_FOREGROUND_SERVICE_SPECIAL_USE;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static boolean opRestrictsRead(int op) {
        throw new RuntimeException();
    }

    /**
     * @return X_Y_Z
     */
    public static String opToName(int op) {
        throw new RuntimeException();
    }

    /**
     * @return android:x_y_z
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static String opToPublicName(int op) {
        throw new RuntimeException();
    }

}
