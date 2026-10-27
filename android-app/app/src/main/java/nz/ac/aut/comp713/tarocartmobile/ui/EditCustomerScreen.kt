package nz.ac.aut.comp713.tarocartmobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.kotlin.awaitFetchPlace
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import nz.ac.aut.comp713.tarocartmobile.BuildConfig
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import nz.ac.aut.comp713.tarocartmobile.data.CurrentLocationAddress
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
    var locating by remember(customer.id) { mutableStateOf(false) }
    var suggestedAddress by remember(customer.id) { mutableStateOf<String?>(null) }
    var addressError by remember(customer.id) { mutableStateOf<String?>(null) }
    var error by remember(customer.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val useCurrentLocation: () -> Unit = {
        locating = true
        suggestedAddress = null
        addressError = null
        error = null
        scope.launch {
            try {
                suggestedAddress = CurrentLocationAddress.lookup(context)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                addressError = exception.message ?: "Could not find your address. Search for it instead."
            } finally {
                locating = false
            }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            useCurrentLocation()
        } else if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            addressError = "Precise location is needed for a street address. Tap Use my current location again and choose Change to precise location."
        } else {
            addressError = "Location access was denied. Allow precise location or search for your address instead."
        }
    }
    val addressSearch = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == PlaceAutocompleteActivity.RESULT_CANCELED) return@rememberLauncherForActivityResult
        if (result.resultCode != PlaceAutocompleteActivity.RESULT_OK) {
            addressError = "Could not search for an address. Please try again."
            return@rememberLauncherForActivityResult
        }
        val intent: Intent = result.data ?: run {
            addressError = "Could not load the selected address. Please try again."
            return@rememberLauncherForActivityResult
        }
        val prediction = PlaceAutocomplete.getPredictionFromIntent(intent)
            ?: run {
                addressError = "Could not load the selected address. Please try again."
                return@rememberLauncherForActivityResult
            }
        val token = PlaceAutocomplete.getSessionTokenFromIntent(intent)
        scope.launch {
            try {
                val response = Places.createClient(context).awaitFetchPlace(
                    prediction.placeId,
                    listOf(Place.Field.FORMATTED_ADDRESS, Place.Field.ADDRESS_COMPONENTS)
                ) {
                    sessionToken = token
                }
                val place = response.place
                val selectedAddress = place.formattedAddress
                val components = place.addressComponents?.asList().orEmpty()
                val types = components.flatMap { it.types }.toSet()
                val country = components.firstOrNull { "country" in it.types }?.shortName
                val hasLocality = types.any { it in setOf("locality", "postal_town", "sublocality_level_1", "postal_code") }
                if (selectedAddress.isNullOrBlank() || selectedAddress.length > 500 ||
                    "street_number" !in types || "route" !in types || !hasLocality || country != "NZ") {
                    addressError = "Select a complete New Zealand street address, including its street number."
                } else {
                    address = selectedAddress
                    suggestedAddress = null
                    addressError = null
                    error = null
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                addressError = exception.message ?: "Could not load the selected address."
            }
        }
    }
    val openAddressSearch: () -> Unit = {
        addressError = null
        try {
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.PLACES_API_KEY)
            }
            addressSearch.launch(PlaceAutocomplete.IntentBuilder()
                .setCountries(listOf("NZ")).build(context))
        } catch (exception: Exception) {
            addressError = exception.message ?: "Could not open address search."
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
                .imePadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Business name *") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = address,
                onValueChange = {
                    if (it.length <= 500) {
                        address = it
                        suggestedAddress = null
                        addressError = null
                        error = null
                    }
                },
                label = { Text("Street address *") },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            if (BuildConfig.PLACES_API_KEY.isNotBlank()) {
                OutlinedButton(onClick = openAddressSearch,
                    enabled = !saving && !locating,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Search New Zealand addresses")
                }
            }
            OutlinedButton(
                enabled = !saving && !locating,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                        useCurrentLocation()
                    } else {
                        locationPermission.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                }
            ) {
                if (locating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Finding nearby address...")
                } else {
                    Text("Use my current location")
                }
            }
            addressError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            suggestedAddress?.let { suggestion ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F9F4)),
                    border = BorderStroke(1.dp, Color(0xFF9BC8AE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Is this your address?", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text(suggestion, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Text("Check the street number and suburb before replacing your saved address.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = {
                                address = suggestion
                                suggestedAddress = null
                                addressError = null
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Use this address") }
                        TextButton(onClick = { suggestedAddress = null }) { Text("Not my address") }
                    }
                }
            }
            OutlinedTextField(
                value = contactName,
                onValueChange = { contactName = it },
                label = { Text("Contact name (optional)") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number (optional)") },
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                enabled = !saving && !locating && suggestedAddress == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        error = "Business name is required."
                        return@Button
                    }
                    if (address.isBlank()) {
                        addressError = "Street address is required."
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
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (saving) "Saving changes..." else "Save changes")
            }
        }
    }
}
