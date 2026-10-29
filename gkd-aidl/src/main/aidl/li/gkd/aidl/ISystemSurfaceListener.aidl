package li.gkd.aidl;

oneway interface ISystemSurfaceListener {
    // -1 unknown, 0 application exposed, 1 system shade covers the application.
    void onSurfaceChanged(int state, long uptimeMillis);
}
