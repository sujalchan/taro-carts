package nz.ac.aut.comp713.tarocartmobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.IOException
import nz.ac.aut.comp713.tarocartmobile.data.MobileAuthApi
import nz.ac.aut.comp713.tarocartmobile.data.MobileApiException
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import nz.ac.aut.comp713.tarocartmobile.data.SessionStore
import nz.ac.aut.comp713.tarocartmobile.ui.AllocationsScreen
import nz.ac.aut.comp713.tarocartmobile.ui.DispatcherScreen
import nz.ac.aut.comp713.tarocartmobile.ui.MobileAuthScreen
import nz.ac.aut.comp713.tarocartmobile.ui.theme.TaroCartMobileTheme

class MainActivity : ComponentActivity() {
    private var hasLocalNetworkAccess by mutableStateOf(false)

    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasLocalNetworkAccess = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasLocalNetworkAccess = canAccessLocalNetwork()
        setContent {
            TaroCartMobileTheme(dynamicColor = false) {
                if (hasLocalNetworkAccess) {
                    TaroCartApp()
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Local network access needed", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text("Allow local network access to sign in and create an account with Taro Carts.")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { requestLocalNetworkAccess() }) {
                            Text("Allow access")
                        }
                    }
                }
            }
        }
        if (!hasLocalNetworkAccess) requestLocalNetworkAccess()
    }

    override fun onResume() {
        super.onResume()
        hasLocalNetworkAccess = canAccessLocalNetwork()
    }

    private fun canAccessLocalNetwork(): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun requestLocalNetworkAccess() {
        if (Build.VERSION.SDK_INT >= 37) {
            localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
}

@Composable
private fun TaroCartApp() {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    val savedSession = remember { store.read() }
    var account by remember { mutableStateOf(savedSession?.first) }
    var sessionToken by remember { mutableStateOf(savedSession?.second) }
    var signInMessage by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = context as? LifecycleOwner
    remember { AllocationApi.setSessionToken(sessionToken) }

    LaunchedEffect(account?.username, sessionToken, lifecycleOwner) {
        val token = sessionToken ?: return@LaunchedEffect
        while (currentCoroutineContext().isActive) {
            val appIsVisible = lifecycleOwner == null ||
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (appIsVisible) {
                try {
                    MobileAuthApi.checkSession(token)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: MobileApiException) {
                    if (exception.statusCode == 401 || exception.statusCode == 403 || exception.errorCode in setOf(
                            "ACCOUNT_DEACTIVATED", "ACCOUNT_PROFILE_UNAVAILABLE", "SESSION_INVALID"
                        )) {
                        store.clear()
                        AllocationApi.setSessionToken(null)
                        account = null
                        sessionToken = null
                        signInMessage = exception.message
                        return@LaunchedEffect
                    }
                } catch (_: IOException) {
                    // A network outage is temporary; keep the session and retry when the app is visible.
                }
            }
            delay(15_000)
        }
    }

    val signOut: suspend () -> Unit = {
        val token = sessionToken
        try {
            if (token != null) MobileAuthApi.logout(token)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val reason = MobileAuthApi.userFacingMessage(exception,
                "The server could not be reached to revoke the session.")
            signInMessage = "You are signed out on this device. The server could not confirm sign-out. $reason"
        } finally {
            store.clear()
            AllocationApi.setSessionToken(null)
            account = null
            sessionToken = null
        }
    }

    when (account?.role) {
        "CUSTOMER", "DISPATCHER" -> {
            if (account?.role == "CUSTOMER") {
                AllocationsScreen(username = account?.username.orEmpty(), onSignOut = signOut)
            } else {
                DispatcherScreen(username = account?.username.orEmpty(), onSignOut = signOut)
            }
        }
        else -> MobileAuthScreen(
            account = account,
            signInMessage = signInMessage,
            onSignedIn = { signedInAccount, token ->
                store.write(signedInAccount, token)
                AllocationApi.setSessionToken(token)
                account = signedInAccount
                sessionToken = token
                signInMessage = null
            },
            onSignOut = signOut
        )
    }
}
