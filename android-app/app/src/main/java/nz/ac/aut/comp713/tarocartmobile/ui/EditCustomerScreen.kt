package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import nz.ac.aut.comp713.tarocartmobile.data.Customer

@Composable
@OptIn(ExperimentalMaterial3Api::class)
// allows the acting customer to update the information stored by customer-service
fun EditCustomerScreen(customer: Customer, onBack: () -> Unit, onSaved: (Customer) -> Unit) {
    var name by remember(customer.id) { mutableStateOf(customer.name) }
    var contactName by remember(customer.id) { mutableStateOf(customer.contactName) }
    var phone by remember(customer.id) { mutableStateOf(customer.phone) }
    var active by remember(customer.id) { mutableStateOf(customer.active) }
    var saving by remember(customer.id) { mutableStateOf(false) }
    var error by remember(customer.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // keeps system back navigation consistent with the app bar action
    BackHandler(enabled = !saving, onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit customer information") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !saving) { Text("Back") }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Customer name") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = contactName,
                onValueChange = { contactName = it },
                label = { Text("Contact name") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone") },
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = active, onCheckedChange = { active = it }, enabled = !saving)
                Text("Active customer")
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !saving,
                    onClick = {
                        val trimmedName = name.trim()
                        if (trimmedName.isEmpty()) {
                            error = "Customer name is required."
                            return@Button
                        }

                        // submits normalized text while preserving the customer's identity
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                val updated = AllocationApi.updateCustomer(
                                    customer.copy(
                                        name = trimmedName,
                                        contactName = contactName.trim(),
                                        phone = phone.trim(),
                                        active = active
                                    )
                                )
                                onSaved(updated)
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                error = exception.message ?: "Could not update customer information."
                            } finally {
                                saving = false
                            }
                        }
                    }
                ) { Text("Save changes") }
                if (saving) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator()
                }
            }
        }
    }
}
