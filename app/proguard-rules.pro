# Keep WebView Javascript interfaces if later added
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
