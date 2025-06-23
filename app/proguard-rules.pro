# http://developer.android.com/guide/developing/tools/proguard.html

-dontwarn **

# https://github.com/getActivity/XXPermissions/issues/370
-keep class androidx.fragment.app.Fragment {
   requestPermissions(...);
}
