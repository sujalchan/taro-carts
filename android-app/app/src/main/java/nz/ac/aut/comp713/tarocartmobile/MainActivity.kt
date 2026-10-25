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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
                    TaroCartApp()
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

// identifies the two top-level views available from the bottom navigation bar
private enum class AppView {
    CUSTOMER,
    DISPATCHER
}

@Composable
private fun TaroCartApp() {
    // preserves the selected view when the activity is recreated, such as after rotation
    var selectedView by rememberSaveable { mutableStateOf(AppView.CUSTOMER) }

    Scaffold(
        bottomBar = {
            // provides a persistent switch between customer and dispatcher views
            NavigationBar {
                NavigationBarItem(
                    selected = selectedView == AppView.CUSTOMER,
                    onClick = { selectedView = AppView.CUSTOMER },
                    icon = { Text("C") },
                    label = { Text("Customer") }
                )
                NavigationBarItem(
                    selected = selectedView == AppView.DISPATCHER,
                    onClick = { selectedView = AppView.DISPATCHER },
                    icon = { Text("D") },
                    label = { Text("Dispatcher") }
                )
            }
        }
    ) { contentPadding ->
        // displays the content belonging to the currently selected navigation item
        when (selectedView) {
            AppView.CUSTOMER -> Column(Modifier.padding(contentPadding)) {
                AllocationsScreen()
            }
            AppView.DISPATCHER -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // reserves the dispatcher view until its mobile workflow is implemented
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Dispatcher",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("TODO: Implement dispatcher mobile functionality")
                    }
                }
            }
        }
    }
}
