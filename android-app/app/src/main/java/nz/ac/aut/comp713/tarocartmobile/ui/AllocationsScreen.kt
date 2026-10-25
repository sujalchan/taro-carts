package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import nz.ac.aut.comp713.tarocartmobile.data.Allocation
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import nz.ac.aut.comp713.tarocartmobile.data.Customer
import java.text.NumberFormat
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AllocationsScreen() {
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var allocations by remember { mutableStateOf<List<Allocation>>(emptyList()) }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var customersError by remember { mutableStateOf<String?>(null) }
    var allocationsError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var customerMenuOpen by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            customers = AllocationApi.getCustomers()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            customersError = exception.message ?: "Could not load customers."
        }
    }

    LaunchedEffect(selectedCustomerId, refreshKey) {
        loading = true
        allocationsError = null
        try {
            allocations = AllocationApi.getAllocations(selectedCustomerId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            allocations = emptyList()
            allocationsError = exception.message ?: "Could not load allocations."
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Taro Allocation System", style = MaterialTheme.typography.titleMedium)
                        Text("Weekly Allocations", style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(contentPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Filter by customer", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(onClick = { customerMenuOpen = true }) {
                                Text(customers.firstOrNull { it.id == selectedCustomerId }?.name ?: "All customers")
                            }
                            DropdownMenu(
                                expanded = customerMenuOpen,
                                onDismissRequest = { customerMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All customers") },
                                    onClick = {
                                        selectedCustomerId = null
                                        customerMenuOpen = false
                                    }
                                )
                                customers.forEach { customer ->
                                    DropdownMenuItem(
                                        text = { Text(customer.name) },
                                        onClick = {
                                            selectedCustomerId = customer.id
                                            customerMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Button(onClick = { refreshKey++ }, modifier = Modifier.padding(top = 18.dp)) {
                        Text("Refresh")
                    }
                }
                customersError?.let {
                    Text(
                        "Customer filter unavailable: $it",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(12.dp))
                        Text("Loading allocations…")
                    }
                }
            } else if (allocationsError != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Unable to load allocations", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(allocationsError.orEmpty())
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { refreshKey++ }) { Text("Try again") }
                        }
                    }
                }
            } else if (allocations.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Text("No weekly allocations found.", Modifier.padding(18.dp))
                    }
                }
            } else {
                items(allocations, key = { it.id }) { allocation ->
                    AllocationCard(allocation)
                }
            }
        }
    }
}

@Composable
private fun AllocationCard(allocation: Allocation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Allocation #${allocation.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            InfoLine("Customer", allocation.customerName)
            InfoLine("Week Start", allocation.weekStart)
            InfoLine("Order Status", allocation.deliveryStatus.readableStatus())
            InfoLine("Payment Status", allocation.paymentStatus.readableStatus())

            if (allocation.items.isEmpty()) {
                Text("No items in this allocation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("Taro Type", Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text("Qty", Modifier.weight(0.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text("Price/kg", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                allocation.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(item.taroTypeName, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
                        Text(item.quantity.toString(), Modifier.weight(0.5f), style = MaterialTheme.typography.bodyMedium)
                        Text(formatPrice(item.pricePerKg), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (allocation.invoiceAvailable) {
                Text("Invoice available", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(116.dp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun String.readableStatus(): String = lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

private fun formatPrice(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale("en", "NZ")).format(amount)
