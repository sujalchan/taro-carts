package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nz.ac.aut.comp713.tarocartmobile.data.Allocation
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import nz.ac.aut.comp713.tarocartmobile.data.Customer
import nz.ac.aut.comp713.tarocartmobile.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import nz.ac.aut.comp713.tarocartmobile.ui.theme.PortalGreen

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AllocationsScreen(
    modifier: Modifier = Modifier,
    username: String = "",
    sessionToken: String = "",
    onSignOut: suspend () -> Unit = {},
    onSessionEnded: (String) -> Unit = {},
    serviceNotice: String? = null,
    onAccountSelected: (Boolean) -> Unit = {}
) {
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var allocations by remember { mutableStateOf<List<Allocation>>(emptyList()) }
    // saves the acting customer's basic details so rotation does not restart selection
    var selectedCustomerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedCustomerName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCustomerAddress by rememberSaveable { mutableStateOf("") }
    var selectedCustomerContactName by rememberSaveable { mutableStateOf("") }
    var selectedCustomerPhone by rememberSaveable { mutableStateOf("") }
    var selectedCustomerActive by rememberSaveable { mutableStateOf(true) }
    var loadingCustomers by remember { mutableStateOf(true) }
    var customersError by remember { mutableStateOf<String?>(null) }
    var allocationsError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var customerRefreshKey by remember { mutableStateOf(0) }
    var refreshKey by remember { mutableStateOf(0) }
    var viewedInvoiceId by remember { mutableStateOf<Long?>(null) }
    var requesting by remember { mutableStateOf(false) }
    var editingCustomer by rememberSaveable { mutableStateOf(false) }
    var changingPassword by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Allocation?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var payTarget by remember { mutableStateOf<Allocation?>(null) }
    var paying by remember { mutableStateOf(false) }
    var paymentError by remember { mutableStateOf<String?>(null) }
    var signingOutFromError by remember { mutableStateOf(false) }
    var signOutErrorFromError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val openMenuLabel = stringResource(R.string.open_menu)
    // tracks whether the customer actions drawer is open or closed
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(selectedCustomerId) { onAccountSelected(selectedCustomerId != null) }

    LaunchedEffect(customerRefreshKey) {
        loadingCustomers = true
        customersError = null
        try {
            customers = AllocationApi.getCustomers()
            val ownCustomer = customers.singleOrNull()
                ?: throw IllegalStateException("This account does not have one linked customer profile.")
            selectedCustomerId = ownCustomer.id
            selectedCustomerName = ownCustomer.name
            selectedCustomerAddress = ownCustomer.address
            selectedCustomerContactName = ownCustomer.contactName
            selectedCustomerPhone = ownCustomer.phone
            selectedCustomerActive = ownCustomer.active
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            customersError = exception.message ?: "Could not load customers."
        } finally {
            loadingCustomers = false
        }
    }

    // prefers refreshed customer data while retaining the saved name during loading
    val customer = selectedCustomerId?.let { customerId ->
        customers.firstOrNull { it.id == customerId }
            ?: selectedCustomerName?.let { customerName ->
                Customer(
                    customerId,
                    customerName,
                    selectedCustomerAddress,
                    selectedCustomerContactName,
                    selectedCustomerPhone,
                    selectedCustomerActive
                )
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
    if (customer == null) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                PortalDrawerContent(
                    title = "Customer portal",
                    username = username,
                    role = "Customer",
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
                        title = { PortalBrandTitle("Customer portal") },
                        navigationIcon = {
                            TextButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("Menu")
                            }
                        }
                    )
                }
            ) { contentPadding ->
                Column(modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (loadingCustomers) CircularProgressIndicator()
                    else {
                        Text(customersError ?: "No customer profile is linked to this account.",
                            color = MaterialTheme.colorScheme.error)
                        Button(onClick = { customerRefreshKey++ }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Try again")
                        }
                        OutlinedButton(
                            enabled = !signingOutFromError,
                            onClick = {
                                signOutErrorFromError = null
                                signingOutFromError = true
                                scope.launch {
                                    try {
                                        onSignOut()
                                    } catch (exception: CancellationException) {
                                        throw exception
                                    } catch (exception: Exception) {
                                        signOutErrorFromError = exception.message
                                            ?: "Could not sign out. Please try again."
                                    } finally {
                                        signingOutFromError = false
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            if (signingOutFromError) CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Sign out")
                        }
                        signOutErrorFromError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
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

    if (editingCustomer) {
        EditCustomerScreen(
            customer = customer,
            onBack = { editingCustomer = false },
            onSaved = { updatedCustomer ->
                selectedCustomerName = updatedCustomer.name
                selectedCustomerAddress = updatedCustomer.address
                selectedCustomerContactName = updatedCustomer.contactName
                selectedCustomerPhone = updatedCustomer.phone
                selectedCustomerActive = updatedCustomer.active
                customers = customers.map {
                    if (it.id == updatedCustomer.id) updatedCustomer else it
                }
                editingCustomer = false
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
        // converts the api date into a readable date for the confirmation message
        val formattedWeekStart = formatDisplayDate(allocation.weekStart)
        AlertDialog(
            onDismissRequest = { payTarget = null },
            title = { Text("Mark this allocation as paid?") },
            text = {
                Text(
                    // applies emphasis only to the date within the surrounding message
                    buildAnnotatedString {
                        append("The allocation for the week starting ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(formattedWeekStart)
                        }
                        append(" will be recorded as paid. The app does not collect money.")
                    }
                )
            },
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

    // keeps secondary customer actions outside the main allocation content
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PortalDrawerContent(
                title = "Customer portal",
                username = username,
                role = "Customer",
                onSignOut = onSignOut,
                onEditInformation = {
                    scope.launch {
                        drawerState.close()
                        editingCustomer = true
                    }
                },
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
                    navigationIcon = {
                        // opens the customer actions drawer from the app bar
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.semantics {
                                contentDescription = openMenuLabel
                            }
                        ) {
                            Text("☰", style = MaterialTheme.typography.headlineSmall)
                        }
                    },
                    title = { PortalBrandTitle("Customer portal") }
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
                if (serviceNotice != null) {
                    item {
                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(serviceNotice, modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                item {
                    PortalHero("Customer portal", "Your allocations",
                        "See your weekly taro requests, payments, and invoices in one place.")
                }
                item {
                    PortalPanel {
                        Text(customer.name, style = MaterialTheme.typography.titleMedium)
                        Text("Manage this week's allocations", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { requesting = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Request allocation")
                        }
                        OutlinedButton(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()) {
                            Text("Refresh allocations")
                        }
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("WEEK STARTING", style = MaterialTheme.typography.labelSmall, color = PortalGreen)
            Text(formatDisplayDate(allocation.weekStart), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
    val badgeColors = when (paymentStatus) {
        "PAID" -> Color(0xFF1B5E20) to Color(0xFFE2F4E5)
        "UNPAID" -> Color(0xFFB3261E) to Color(0xFFFCE8E6)
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

// formats an iso date with an ordinal day and falls back to the original value if invalid
private fun formatDisplayDate(value: String): String = runCatching {
    val date = LocalDate.parse(value)
    val day = date.dayOfMonth
    // handles the special teen values before applying the final-digit suffix rules
    val suffix = if (day in 11..13) {
        "th"
    } else {
        when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    "$day$suffix ${date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))}"
}.getOrDefault(value)
