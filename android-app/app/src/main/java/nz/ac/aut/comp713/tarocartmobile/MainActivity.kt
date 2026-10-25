package nz.ac.aut.comp713.tarocartmobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.ac.aut.comp713.tarocartmobile.ui.AllocationsScreen
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
                    AllocationsScreen()
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Local network access needed", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text("Allow access to load allocations from the Taro Carts service on this computer.")
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
