package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var loadingCustomers by remember { mutableStateOf(true) }
    var customersError by remember { mutableStateOf<String?>(null) }
    var allocationsError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var customerRefreshKey by remember { mutableStateOf(0) }
    var refreshKey by remember { mutableStateOf(0) }
    var viewedInvoiceId by remember { mutableStateOf<Long?>(null) }
    var requesting by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Allocation?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var payTarget by remember { mutableStateOf<Allocation?>(null) }
    var paying by remember { mutableStateOf(false) }
    var paymentError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(customerRefreshKey) {
        loadingCustomers = true
        customersError = null
        try {
            customers = AllocationApi.getCustomers()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            customersError = exception.message ?: "Could not load customers."
        } finally {
            loadingCustomers = false
        }
    }

    val customer = selectedCustomer
    if (customer == null) {
        CustomerSelectionScreen(
            customers = customers,
            loading = loadingCustomers,
            error = customersError,
            onRetry = { customerRefreshKey++ },
            onSelect = { selectedCustomer = it }
        )
        return
    }

    viewedInvoiceId?.let { invoiceId ->
        InvoicePdfViewer(allocationId = invoiceId, onBack = { viewedInvoiceId = null })
        return
    }

    if (requesting) {
        RequestAllocationScreen(
            customer = customer,
            onBack = { requesting = false },
            onCreated = {
                requesting = false
                refreshKey++
            }
        )
        return
    }

    LaunchedEffect(customer.id, refreshKey) {
        loading = true
        allocationsError = null
        try {
            allocations = AllocationApi.getAllocations(customer.id)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            allocations = emptyList()
            allocationsError = exception.message ?: "Could not load allocations."
        } finally {
            loading = false
        }
    }

    deleteTarget?.let { allocation ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this request?") },
            text = { Text("Allocation #${allocation.id} will be removed while it is still requested.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    deleting = true
                    deleteError = null
                    scope.launch {
                        try {
                            AllocationApi.deleteCustomerRequest(allocation.id, customer.id)
                            refreshKey++
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            deleteError = exception.message ?: "Could not delete the request."
                            refreshKey++
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    payTarget?.let { allocation ->
        AlertDialog(
            onDismissRequest = { payTarget = null },
            title = { Text("Mark this allocation as paid?") },
            text = { Text("Allocation #${allocation.id} will be recorded as paid. The app does not collect money.") },
            confirmButton = {
                TextButton(onClick = {
                    payTarget = null
                    paying = true
                    paymentError = null
                    scope.launch {
                        try {
                            AllocationApi.recordPayment(allocation.id, customer.id)
                            refreshKey++
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            paymentError = exception.message ?: "Could not record payment."
                            refreshKey++
                        } finally {
                            paying = false
                        }
                    }
                }) { Text("Mark paid") }
            },
            dismissButton = { TextButton(onClick = { payTarget = null }) { Text("Cancel") } }
        )
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Acting as ${customer.name}", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            selectedCustomer = null
                            allocations = emptyList()
                            deleteError = null
                            paymentError = null
                            customerRefreshKey++
                        }) { Text("Switch customer") }
                        OutlinedButton(onClick = { refreshKey++ }) { Text("Refresh") }
                    }
                    Button(onClick = { requesting = true }) { Text("Request allocation") }
                }
                deleteError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                paymentError?.let {
                    Text(
                        it,
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
                    AllocationCard(
                        allocation,
                        actingCustomerId = customer.id,
                        deleting = deleting,
                        onDelete = { deleteTarget = allocation },
                        paying = paying,
                        onPay = { payTarget = allocation },
                        onViewInvoice = { viewedInvoiceId = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun AllocationCard(
    allocation: Allocation,
    actingCustomerId: Long,
    deleting: Boolean,
    onDelete: () -> Unit,
    paying: Boolean,
    onPay: () -> Unit,
    onViewInvoice: (Long) -> Unit
) {
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
            InfoLine(
                "Payment Status",
                allocation.paymentStatus.readableStatus(),
                paymentStatus = allocation.paymentStatus
            )

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
                OutlinedButton(onClick = { onViewInvoice(allocation.id) }) {
                    Text("View invoice PDF")
                }
            }
            if (allocation.customerId == actingCustomerId
                && allocation.deliveryStatus != "REQUESTED_ALLOCATION"
                && allocation.deliveryStatus != "CANCELLED"
                && allocation.paymentStatus != "PAID") {
                Button(onClick = onPay, enabled = !paying) { Text("Pay") }
            }
            if (allocation.customerId == actingCustomerId
                && allocation.requestedByCustomerId == actingCustomerId
                && allocation.deliveryStatus == "REQUESTED_ALLOCATION") {
                TextButton(onClick = onDelete, enabled = !deleting) { Text("Delete request") }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, paymentStatus: String? = null) {
    val darkTheme = isSystemInDarkTheme()
    val badgeColors = when (paymentStatus) {
        "PAID" -> if (darkTheme) Color(0xFFB9F6CA) to Color(0xFF174B31)
            else Color(0xFF1B5E20) to Color(0xFFE2F4E5)
        "UNPAID" -> if (darkTheme) Color(0xFFFFB4AB) to Color(0xFF5C1D1B)
            else Color(0xFFB3261E) to Color(0xFFFCE8E6)
        else -> null
    }
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(116.dp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            modifier = badgeColors?.let { (_, background) ->
                Modifier.background(background, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            } ?: Modifier,
            color = badgeColors?.first ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = if (badgeColors != null) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun String.readableStatus(): String = lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

private fun formatPrice(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ")).format(amount)
