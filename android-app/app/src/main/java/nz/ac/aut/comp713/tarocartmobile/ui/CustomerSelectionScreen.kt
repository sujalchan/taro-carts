package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import nz.ac.aut.comp713.tarocartmobile.data.Customer

@Composable
@OptIn(ExperimentalMaterial3Api::class)
// displays loading, error, empty, and selectable customer states in one screen
fun CustomerSelectionScreen(
    customers: List<Customer>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelect: (Customer) -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { PortalBrandTitle("Customers") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PortalHero("Customer portal", "Choose a customer",
                "Select the customer you want to act as.")
            // chooses the single state that should be visible for the current customer result
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                error != null -> PortalPanel {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("Try again") }
                }
                customers.isEmpty() -> PortalPanel {
                    Text("No active customers are available.")
                    Button(onClick = onRetry) { Text("Refresh customers") }
                }
                else -> LazyColumn(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // uses stable customer ids so compose can preserve each list item correctly
                    items(customers, key = { it.id }) { customer ->
                        Card(
                            onClick = { onSelect(customer) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(customer.name, style = MaterialTheme.typography.titleMedium)
                                Text(customer.address, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
