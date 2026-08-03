package android.app;

import android.os.Build;

import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

@RemapType(AppOpsManager.class)
public class AppOpsManagerHidden {
    public static int OP_POST_NOTIFICATION;

    public static int OP_SYSTEM_ALERT_WINDOW;

    @RequiresApi(Build.VERSION_CODES.Q)
    public static int OP_ACCESS_ACCESSIBILITY;

    @RequiresApi(Build.VERSION_CODES.Q)
    public static String OPSTR_ACCESS_ACCESSIBILITY;

    // 14.0.0_r29 - 14.0.0_r37, 14.0.0_r50 - 17
    public static int OP_CREATE_ACCESSIBILITY_OVERLAY;

    // 14.0.0_r29 - 14.0.0_r37, 14.0.0_r50 - 17
    public static String OPSTR_CREATE_ACCESSIBILITY_OVERLAY;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static int OP_ACCESS_RESTRICTED_SETTINGS;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static String OPSTR_ACCESS_RESTRICTED_SETTINGS;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static int OP_FOREGROUND_SERVICE_SPECIAL_USE;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static String OPSTR_FOREGROUND_SERVICE_SPECIAL_USE;
}
