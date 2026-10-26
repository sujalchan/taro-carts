package nz.ac.aut.comp713.tarocartmobile.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.kotlin.awaitFetchPlace
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import nz.ac.aut.comp713.tarocartmobile.BuildConfig
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import nz.ac.aut.comp713.tarocartmobile.data.Customer

@Composable
@OptIn(ExperimentalMaterial3Api::class)
// allows the acting customer to update the information stored by customer-service
fun EditCustomerScreen(customer: Customer, onBack: () -> Unit, onSaved: (Customer) -> Unit) {
    val context = LocalContext.current
    var name by remember(customer.id) { mutableStateOf(customer.name) }
    var address by remember(customer.id) { mutableStateOf(customer.address) }
    var contactName by remember(customer.id) { mutableStateOf(customer.contactName) }
    var phone by remember(customer.id) { mutableStateOf(customer.phone) }
    var saving by remember(customer.id) { mutableStateOf(false) }
    var error by remember(customer.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val addressSearch = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val intent: Intent = result.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != PlaceAutocompleteActivity.RESULT_OK) return@rememberLauncherForActivityResult
        val prediction = PlaceAutocomplete.getPredictionFromIntent(intent)
            ?: return@rememberLauncherForActivityResult
        val token = PlaceAutocomplete.getSessionTokenFromIntent(intent)
        scope.launch {
            try {
                val response = Places.createClient(context).awaitFetchPlace(
                    prediction.placeId, listOf(Place.Field.FORMATTED_ADDRESS)
                ) {
                    sessionToken = token
                }
                val selectedAddress = response.place.formattedAddress
                if (selectedAddress.isNullOrBlank() || selectedAddress.length > 500) {
                    error = "Please enter this address manually."
                } else {
                    address = selectedAddress
                    error = null
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = exception.message ?: "Could not load the selected address."
            }
        }
    }

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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
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
                value = address,
                onValueChange = { if (it.length <= 500) address = it },
                label = { Text("Address") },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            if (BuildConfig.PLACES_API_KEY.isNotBlank()) {
                TextButton(onClick = {
                    try {
                        if (!Places.isInitialized()) {
                            Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.PLACES_API_KEY)
                        }
                        addressSearch.launch(
                            PlaceAutocomplete.IntentBuilder()
                                .setCountries(listOf("NZ"))
                                .build(context)
                        )
                    } catch (exception: Exception) {
                        error = exception.message ?: "Could not open address search."
                    }
                }) { Text("Search address with Google") }
            }
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
                        if (address.isBlank()) {
                            error = "Customer address is required."
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
                                        address = address.trim(),
                                        contactName = contactName.trim(),
                                        phone = phone.trim()
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
