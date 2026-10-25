package nz.ac.aut.comp713.tarocartmobile.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import nz.ac.aut.comp713.tarocartmobile.data.Customer
import nz.ac.aut.comp713.tarocartmobile.data.RequestedItem
import nz.ac.aut.comp713.tarocartmobile.data.TaroType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class ItemDraft(val key: Int, val taroTypeId: Long? = null, val quantity: String = "")

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RequestAllocationScreen(customer: Customer, onBack: () -> Unit, onCreated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var weekStart by remember { mutableStateOf(nextMonday()) }
    var taroTypes by remember { mutableStateOf<List<TaroType>>(emptyList()) }
    var loadingTypes by remember { mutableStateOf(true) }
    var typesError by remember { mutableStateOf<String?>(null) }
    var typesRefreshKey by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf(listOf(ItemDraft(0))) }
    var nextRowKey by remember { mutableIntStateOf(1) }
    var openMenuKey by remember { mutableStateOf<Int?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(typesRefreshKey) {
        loadingTypes = true
        typesError = null
        try {
            taroTypes = AllocationApi.getTaroTypes()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            typesError = exception.message ?: "Could not load taro types."
        } finally {
            loadingTypes = false
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Request weekly allocation") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Requesting as ${customer.name}", style = MaterialTheme.typography.titleMedium)
                Text("New allocations start as Requested allocation and Unpaid.")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Week starting", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = {
                        val selected = parseWeek(weekStart)
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> weekStart = mondayOf(year, month, day) },
                            selected.get(Calendar.YEAR),
                            selected.get(Calendar.MONTH),
                            selected.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) { Text(weekStart) }
                }
            }
            item {
                when {
                    loadingTypes -> CircularProgressIndicator()
                    typesError != null -> Column {
                        Text(typesError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { typesRefreshKey++ }) { Text("Try again") }
                    }
                    taroTypes.isEmpty() -> Text("No taro types are available to request.")
                    else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        rows.forEachIndexed { index, row ->
                            val selected = taroTypes.firstOrNull { it.id == row.taroTypeId }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Taro item ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                androidx.compose.foundation.layout.Box {
                                    OutlinedButton(onClick = { openMenuKey = row.key }) {
                                        Text(selected?.name ?: "Choose taro type")
                                    }
                                    DropdownMenu(
                                        expanded = openMenuKey == row.key,
                                        onDismissRequest = { openMenuKey = null }
                                    ) {
                                        taroTypes.filter { type ->
                                            type.id == row.taroTypeId || rows.none { it.taroTypeId == type.id }
                                        }.forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type.name) },
                                                onClick = {
                                                    rows = rows.map {
                                                        if (it.key == row.key) it.copy(taroTypeId = type.id) else it
                                                    }
                                                    openMenuKey = null
                                                }
                                            )
                                        }
                                    }
                                }
                                selected?.let {
                                    Text("Standard price: ${formatPrice(it.standardPrice)} per kg")
                                }
                                OutlinedTextField(
                                    value = row.quantity,
                                    onValueChange = { value ->
                                        if (value.all(Char::isDigit)) {
                                            rows = rows.map {
                                                if (it.key == row.key) it.copy(quantity = value) else it
                                            }
                                        }
                                    },
                                    label = { Text("Quantity (kg)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (rows.size > 1) {
                                    TextButton(onClick = { rows = rows.filterNot { it.key == row.key } }) {
                                        Text("Remove item")
                                    }
                                }
                            }
                        }
                        if (rows.size < taroTypes.size) {
                            OutlinedButton(onClick = {
                                rows = rows + ItemDraft(nextRowKey)
                                nextRowKey++
                            }) { Text("Add another taro type") }
                        }
                    }
                }
            }
            formError?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !submitting && !loadingTypes && typesError == null && taroTypes.isNotEmpty(),
                        onClick = {
                            val items = rows.mapNotNull { row ->
                                val quantity = row.quantity.toIntOrNull()
                                if (row.taroTypeId != null && quantity != null && quantity > 0) {
                                    RequestedItem(row.taroTypeId, quantity)
                                } else null
                            }
                            if (items.size != rows.size || items.map { it.taroTypeId }.distinct().size != items.size) {
                                formError = "Choose a different taro type and a positive whole quantity for each item."
                            } else {
                                formError = null
                                submitting = true
                                scope.launch {
                                    try {
                                        AllocationApi.requestAllocation(customer.id, weekStart, items)
                                        onCreated()
                                    } catch (exception: CancellationException) {
                                        throw exception
                                    } catch (exception: Exception) {
                                        formError = exception.message ?: "Could not request the allocation."
                                    } finally {
                                        submitting = false
                                    }
                                }
                            }
                        }
                    ) { Text("Submit request") }
                    if (submitting) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun nextMonday(): String {
    val calendar = Calendar.getInstance()
    val daysAhead = (Calendar.MONDAY - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
    calendar.add(Calendar.DAY_OF_MONTH, if (daysAhead == 0) 7 else daysAhead)
    return formatWeek(calendar)
}

private fun mondayOf(year: Int, month: Int, day: Int): String {
    val calendar = Calendar.getInstance().apply { set(year, month, day) }
    val daysAfterMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    calendar.add(Calendar.DAY_OF_MONTH, -daysAfterMonday)
    return formatWeek(calendar)
}

private fun parseWeek(value: String): Calendar = Calendar.getInstance().apply {
    time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value) ?: time
}

private fun formatWeek(calendar: Calendar): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

private fun formatPrice(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ")).format(amount)
