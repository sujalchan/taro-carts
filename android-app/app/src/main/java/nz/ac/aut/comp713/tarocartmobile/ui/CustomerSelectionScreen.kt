package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
fun CustomerSelectionScreen(
    customers: List<Customer>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelect: (Customer) -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Choose a customer") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Select the customer you want to act as.", style = MaterialTheme.typography.bodyLarge)
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                error != null -> {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("Try again") }
                }
                customers.isEmpty() -> {
                    Text("No active customers are available.")
                    Button(onClick = onRetry) { Text("Refresh customers") }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(customers, key = { it.id }) { customer ->
                        Button(onClick = { onSelect(customer) }, modifier = Modifier.fillMaxWidth()) {
                            Text(customer.name)
                        }
                    }
                }
            }
        }
    }
}
