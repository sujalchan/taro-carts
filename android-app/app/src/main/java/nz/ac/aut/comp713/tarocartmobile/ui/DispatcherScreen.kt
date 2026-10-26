package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import nz.ac.aut.comp713.tarocartmobile.data.AssignedDelivery
import nz.ac.aut.comp713.tarocartmobile.data.Dispatcher
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DispatcherScreen(onAccountSelected: (Boolean) -> Unit = {}) {
    var dispatchers by remember { mutableStateOf<List<Dispatcher>>(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedName by rememberSaveable { mutableStateOf("") }
    var loadingDispatchers by remember { mutableStateOf(true) }
    var dispatcherError by remember { mutableStateOf<String?>(null) }
    var dispatcherRefresh by remember { mutableStateOf(0) }
    var deliveries by remember { mutableStateOf<List<AssignedDelivery>>(emptyList()) }
    var loadingDeliveries by remember { mutableStateOf(false) }
    var deliveriesError by remember { mutableStateOf<String?>(null) }
    var deliveriesRefresh by remember { mutableStateOf(0) }

    LaunchedEffect(selectedId) { onAccountSelected(selectedId != null) }

    LaunchedEffect(dispatcherRefresh) {
        loadingDispatchers = true
        dispatcherError = null
        try {
            dispatchers = AllocationApi.getDispatchers()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            dispatcherError = exception.message ?: "Could not load dispatchers."
        } finally {
            loadingDispatchers = false
        }
    }

    LaunchedEffect(selectedId, deliveriesRefresh) {
        val dispatcherId = selectedId ?: return@LaunchedEffect
        loadingDeliveries = true
        deliveriesError = null
        deliveries = emptyList()
        try {
            deliveries = AllocationApi.getAssignedDeliveries(dispatcherId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            deliveriesError = exception.message ?: "Could not load assigned deliveries."
        } finally {
            loadingDeliveries = false
        }
    }

    BackHandler(selectedId != null) {
        deliveries = emptyList()
        selectedId = null
        onAccountSelected(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedId == null) "Choose a dispatcher" else selectedName) },
                actions = {
                    if (selectedId != null) {
                        TextButton(onClick = {
                            deliveries = emptyList()
                            selectedId = null
                            onAccountSelected(false)
                        }) { Text("Switch user") }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            if (selectedId == null) {
                Text("Select the dispatcher you want to act as.")
                when {
                    loadingDispatchers -> CircularProgressIndicator(Modifier.padding(top = 20.dp))
                    dispatcherError != null -> {
                        Text(dispatcherError.orEmpty(), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp))
                        Button(onClick = { dispatcherRefresh++ }) { Text("Try again") }
                    }
                    dispatchers.isEmpty() -> {
                        Text("No active dispatchers are available.", Modifier.padding(top = 16.dp))
                        Button(onClick = { dispatcherRefresh++ }) { Text("Refresh dispatchers") }
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(dispatchers, key = { it.id }) { dispatcher ->
                            Button(onClick = {
                                deliveries = emptyList()
                                loadingDeliveries = true
                                selectedId = dispatcher.id
                                selectedName = dispatcher.name
                                onAccountSelected(true)
                            }, modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(24.dp)) {
                                Text(dispatcher.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Assigned deliveries", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { deliveriesRefresh++ }) { Text("Refresh") }
                }
                when {
                    loadingDeliveries -> CircularProgressIndicator(Modifier.padding(top = 20.dp))
                    deliveriesError != null -> {
                        Text(deliveriesError.orEmpty(), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp))
                        Button(onClick = { deliveriesRefresh++ }) { Text("Try again") }
                    }
                    deliveries.isEmpty() -> Text("No allocations are assigned to you.",
                        modifier = Modifier.padding(top = 16.dp))
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(deliveries, key = { it.allocation.id }) { delivery ->
                            DeliveryCard(delivery)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryCard(delivery: AssignedDelivery) {
    val allocation = delivery.allocation
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(delivery.customer.name, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("Address", style = MaterialTheme.typography.labelMedium)
            Text(delivery.customer.address.ifBlank { "No address recorded" })
            Text("Delivery week", style = MaterialTheme.typography.labelMedium)
            Text(formatDeliveryWeek(allocation.weekStart))
            Text("Status: ${allocation.deliveryStatus.lowercase().replace('_', ' ')}")
            Text("Taro allocation", style = MaterialTheme.typography.labelMedium)
            if (allocation.items.isEmpty()) {
                Text("No taro items recorded")
            } else {
                allocation.items.forEach { item ->
                    Text("${item.taroTypeName}: ${item.quantity} kg")
                }
            }
        }
    }
}

private fun formatDeliveryWeek(weekStart: String): String = runCatching {
    val monday = LocalDate.parse(weekStart)
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    "${monday.format(formatter)} – ${monday.plusDays(6).format(formatter)}"
}.getOrDefault(weekStart)
