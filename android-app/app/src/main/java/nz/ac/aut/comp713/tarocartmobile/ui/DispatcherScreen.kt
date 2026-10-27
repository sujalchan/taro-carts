package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import nz.ac.aut.comp713.tarocartmobile.data.AssignedDelivery
import nz.ac.aut.comp713.tarocartmobile.data.Dispatcher
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DispatcherScreen(
    modifier: Modifier = Modifier,
    username: String = "",
    sessionToken: String = "",
    onSignOut: suspend () -> Unit = {},
    onSessionEnded: (String) -> Unit = {},
    serviceNotice: String? = null,
    onAccountSelected: (Boolean) -> Unit = {}
) {
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
    var updatingDeliveryId by remember { mutableStateOf<Long?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var changingPassword by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedId) { onAccountSelected(selectedId != null) }

    LaunchedEffect(dispatcherRefresh) {
        loadingDispatchers = true
        dispatcherError = null
        try {
            dispatchers = AllocationApi.getDispatchers()
            val ownDispatcher = dispatchers.singleOrNull()
                ?: throw IllegalStateException("This account does not have one linked dispatcher profile.")
            selectedId = ownDispatcher.id
            selectedName = ownDispatcher.name
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

    if (changingPassword) {
        ChangePasswordScreen(
            sessionToken = sessionToken,
            onBack = { changingPassword = false },
            onSessionEnded = onSessionEnded
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            PortalDrawerContent(
                title = "Dispatcher portal",
                username = username,
                role = "Dispatcher",
                onSignOut = onSignOut,
                onChangePassword = {
                    scope.launch {
                        drawerState.close()
                        changingPassword = true
                    }
                }
            )
        }
    ) {
        Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { PortalBrandTitle("Dispatcher portal") },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Text("☰", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PortalHero("Dispatcher portal", "Your deliveries",
                "See assigned deliveries and keep the week moving.")
            if (serviceNotice != null) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(serviceNotice, modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (selectedId == null) {
                when {
                    loadingDispatchers -> CircularProgressIndicator(Modifier.padding(top = 20.dp))
                    dispatcherError != null -> {
                        PortalPanel {
                            Text(dispatcherError.orEmpty(), color = MaterialTheme.colorScheme.error)
                            Button(onClick = { dispatcherRefresh++ }) { Text("Try again") }
                        }
                    }
                }
            } else {
                PortalPanel {
                    Text("Deliveries for $selectedName", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { deliveriesRefresh++ },
                        modifier = Modifier.fillMaxWidth()) { Text("Refresh deliveries") }
                }
                when {
                    loadingDispatchers -> CircularProgressIndicator(Modifier.padding(top = 20.dp))
                    loadingDeliveries -> CircularProgressIndicator(Modifier.padding(top = 20.dp))
                    deliveriesError != null -> PortalPanel {
                        Text(deliveriesError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { deliveriesRefresh++ }) { Text("Try again") }
                    }
                    deliveries.isEmpty() -> PortalPanel {
                        Text("No allocations are assigned to you.")
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(deliveries, key = { it.allocation.id }) { delivery ->
                            DeliveryCard(delivery,
                                updating = updatingDeliveryId == delivery.allocation.id,
                                onAdvance = { status ->
                                    updateError = null
                                    updatingDeliveryId = delivery.allocation.id
                                    scope.launch {
                                        try {
                                            AllocationApi.advanceDelivery(delivery.allocation.id, status)
                                            deliveriesRefresh++
                                        } catch (exception: CancellationException) {
                                            throw exception
                                        } catch (exception: Exception) {
                                            updateError = exception.message ?: "Could not update delivery status."
                                        } finally { updatingDeliveryId = null }
                                    }
                                })
                        }
                    }
                }
                updateError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
    }
}

@Composable
private fun DeliveryCard(delivery: AssignedDelivery, updating: Boolean,
                         onAdvance: (String) -> Unit) {
    val allocation = delivery.allocation
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(allocation.customerName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium)
            if (allocation.deliveryStatus != "DELIVERED") {
                Text("Delivery address", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                Text(allocation.deliveryAddress ?: "No delivery address set")
            }
            Text("Delivery week", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
            Text(formatDeliveryWeek(allocation.weekStart))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                DeliveryStatusBadge(allocation.deliveryStatus)
            }
            Text("Taro allocation", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
            if (allocation.items.isEmpty()) {
                Text("No taro items recorded")
            } else {
                allocation.items.forEach { item ->
                    Text("${item.taroTypeName}: ${item.quantity} kg")
                }
            }
            when (allocation.deliveryStatus) {
                "AWAITING_DISPATCH" -> Button(enabled = !updating,
                    onClick = { onAdvance("IN_TRANSIT") }) {
                    if (updating) CircularProgressIndicator() else Text("Start delivery")
                }
                "IN_TRANSIT" -> Button(enabled = !updating,
                    onClick = { onAdvance("DELIVERED") }) {
                    if (updating) CircularProgressIndicator() else Text("Mark delivered")
                }
            }
        }
    }
}

@Composable
private fun DeliveryStatusBadge(status: String) {
    val (background, foreground) = when (status) {
        "REQUESTED_ALLOCATION" -> Color(0xFFE5E7EB) to Color(0xFF374151)
        "APPROVED_ALLOCATION" -> Color(0xFFD7F2EC) to Color(0xFF075E4E)
        "PREPARING_ALLOCATION" -> Color(0xFFFFF1C2) to Color(0xFF6B4F00)
        "AWAITING_DISPATCH" -> Color(0xFFFFE4D1) to Color(0xFF8A390C)
        "IN_TRANSIT" -> Color(0xFFDCEBFF) to Color(0xFF174E83)
        "DELIVERED" -> Color(0xFFDCF5DF) to Color(0xFF1B5E20)
        "CANCELLED" -> Color(0xFFFCE3E1) to Color(0xFF9C2F28)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    val label = status.lowercase(Locale.ENGLISH).replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(background)
        .padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, color = foreground, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold)
    }
}

private fun formatDeliveryWeek(weekStart: String): String = runCatching {
    val monday = LocalDate.parse(weekStart)
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    "${monday.format(formatter)} – ${monday.plusDays(6).format(formatter)}"
}.getOrDefault(weekStart)
