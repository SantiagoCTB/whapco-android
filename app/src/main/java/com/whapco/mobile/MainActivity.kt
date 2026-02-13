package com.whapco.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingIndicator: LinearProgressIndicator
    private lateinit var offlineContainer: LinearLayout

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { granted -> !granted }.keys
        if (denied.isNotEmpty()) {
            Log.w(TAG, "Permisos denegados: $denied")
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        if (callback == null) {
            return@registerForActivityResult
        }

        val data = result.data
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
        callback.onReceiveValue(uris)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        offlineContainer = findViewById(R.id.offlineContainer)
        findViewById<Button>(R.id.retryButton).setOnClickListener {
            if (isOnline()) {
                showOffline(false)
                webView.reload()
            }
        }

        setupBackNavigation()
        configureWebView()

        if (!isOnline()) {
            showOffline(true)
        }

        if (savedInstanceState == null) {
            val targetUrl = intent?.dataString ?: START_URL
            webView.loadUrl(targetUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { deepLink ->
            if (isAllowedUrl(deepLink)) {
                webView.loadUrl(deepLink)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
            flush()
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString WhapcoAndroidWebView/1.0"
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val targetUrl = request.url.toString()
                return if (isAllowedUrl(targetUrl)) {
                    false
                } else {
                    Log.w(TAG, "Bloqueando navegación externa: $targetUrl")
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                showLoading(true)
                Log.i(TAG, "Cargando URL: $url")
                if (url.contains("/logout")) {
                    clearSessionCookies()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                showLoading(false)
                CookieManager.getInstance().flush()
                Log.i(TAG, "Página lista: $url")
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Log.e(TAG, "Error principal: ${error.description} (${error.errorCode})")
                    showOffline(true)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val resources = request.resources
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    runOnUiThread {
                        request.grant(resources)
                    }
                }
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = view
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                ensureMediaPermissions()

                return try {
                    val intent = fileChooserParams.createIntent().apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    filePickerLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error lanzando selector de archivos", e)
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    offlineContainer.visibility == View.VISIBLE -> finish()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })
    }

    private fun ensureMediaPermissions() {
        val permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun isAllowedUrl(url: String): Boolean {
        val parsed = Uri.parse(url)
        val host = parsed.host ?: return false
        val isHttps = parsed.scheme.equals("https", ignoreCase = true)

        return isHttps && ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun isOnline(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun clearSessionCookies() {
        val manager = CookieManager.getInstance()
        manager.removeSessionCookies(null)
        manager.flush()
        Log.i(TAG, "Sesión local limpiada por logout")
    }

    private fun showOffline(show: Boolean) {
        offlineContainer.visibility = if (show) View.VISIBLE else View.GONE
        webView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    companion object {
        private const val TAG = "WhapcoWebView"
        private const val START_URL = "https://app.whapco.example.com/mobile"
        private val ALLOWED_HOSTS = setOf(
            "app.whapco.example.com",
            "cdn.whapco.example.com"
        )
    }
}
