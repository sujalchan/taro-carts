package nz.ac.aut.comp713.tarocartmobile.ui

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/** Reuses the authenticated dispatcher portal's three-point route and truck marker. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DispatcherNavigationScreen(allocationId: Long, sessionToken: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val url = "http://10.0.2.2:8084/portal/deliveries/$allocationId/locations?embedded=true"
    val webView = remember(allocationId) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = WebViewClient()
        }
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(webView, sessionToken) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie(url, "SESSION=$sessionToken; Path=/; SameSite=Lax") {
                webView.loadUrl(url)
            }
            flush()
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Navigate") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
    }) { padding ->
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
