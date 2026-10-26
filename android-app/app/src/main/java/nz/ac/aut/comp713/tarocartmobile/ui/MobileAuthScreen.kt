package nz.ac.aut.comp713.tarocartmobile.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.kotlin.awaitFetchPlace
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import nz.ac.aut.comp713.tarocartmobile.BuildConfig
import nz.ac.aut.comp713.tarocartmobile.data.MobileAccount
import nz.ac.aut.comp713.tarocartmobile.data.MobileAuthApi

@Composable
fun MobileAuthScreen(
    account: MobileAccount?,
    onSignedIn: (MobileAccount, String) -> Unit,
    onSignOut: suspend () -> Unit
) {
    var registering by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(1) }
    var role by remember { mutableStateOf("CUSTOMER") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val addressSearch = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val intent: Intent = result.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != PlaceAutocompleteActivity.RESULT_OK) return@rememberLauncherForActivityResult
        val prediction = PlaceAutocomplete.getPredictionFromIntent(intent)
            ?: return@rememberLauncherForActivityResult
        val sessionToken = PlaceAutocomplete.getSessionTokenFromIntent(intent)
        scope.launch {
            try {
                val selectedAddress = Places.createClient(context).awaitFetchPlace(
                    prediction.placeId, listOf(Place.Field.FORMATTED_ADDRESS)
                ) { this.sessionToken = sessionToken }.place.formattedAddress
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center) {
        Text("Taro Carts", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (account != null) {
            Text("Signed in as ${account.username}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (account.role == "ADMIN") {
                Text("Admin accounts must be viewed on the web app.")
            } else {
                Text(if (account.role == "CUSTOMER")
                    "Your customer account is signed in. Use the customer portal to view or request allocations."
                else "Your dispatcher account is signed in. Use the dispatcher portal to view assigned deliveries.")
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        onSignOut()
                        error = null
                    } catch (exception: Exception) {
                        error = exception.message ?: "Could not sign out. Check your connection and retry."
                    } finally { busy = false }
                }
            }) { Text("Sign out") }
        } else if (registering) {
            Text("Create an account · Step $step of 3", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            when (step) {
                1 -> {
                    Text("What type of account would you like to create?")
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { role = "CUSTOMER" }) { Text(if (role == "CUSTOMER") "✓ Customer" else "Customer") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { role = "DISPATCHER" }) { Text(if (role == "DISPATCHER") "✓ Dispatcher" else "Dispatcher") }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { step = 2 }) { Text("Continue") }
                }
                2 -> {
                    Text("Choose your sign-in details.")
                    Spacer(Modifier.height(12.dp))
                    Field("Username", username, { username = it })
                    Field("Password (at least 12 characters)", password, { password = it }, password = true)
                    Field("Confirm password", confirmPassword, { confirmPassword = it }, password = true)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { step = 1 }) { Text("Back") }
                    Button(onClick = {
                        error = when {
                            username.length < 3 -> "Username must be at least 3 characters."
                            password.length < 12 -> "Password must be at least 12 characters."
                            password != confirmPassword -> "Passwords do not match."
                            else -> null
                        }
                        if (error == null) step = 3
                    }) { Text("Continue") }
                }
                else -> {
                    Text("Tell us about your ${role.lowercase()} profile.")
                    Spacer(Modifier.height(12.dp))
                    Field("Name", name, { name = it })
                    if (role == "CUSTOMER") Field("Contact name", contactName, { contactName = it })
                    Field("Phone", phone, { phone = it })
                    Field("Address", address, { if (it.length <= 500) address = it }, singleLine = false)
                    if (BuildConfig.PLACES_API_KEY.isNotBlank()) {
                        OutlinedButton(onClick = {
                            try {
                                if (!Places.isInitialized()) {
                                    Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.PLACES_API_KEY)
                                }
                                addressSearch.launch(PlaceAutocomplete.IntentBuilder()
                                    .setCountries(listOf("NZ")).build(context))
                            } catch (exception: Exception) {
                                error = exception.message ?: "Could not open address search."
                            }
                        }) { Text("Search address with Google") }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { step = 2 }) { Text("Back") }
                    Button(enabled = !busy, onClick = {
                        error = when {
                            name.isBlank() || address.isBlank() -> "Name and address are required."
                            phone.isNotBlank() && !phone.matches(Regex("^\\+?[0-9]{7,15}$")) -> "Enter a valid phone number."
                            else -> null
                        }
                        if (error == null) {
                            busy = true
                            scope.launch {
                                try {
                                    message = MobileAuthApi.register(role, username.trim(), password, confirmPassword,
                                        name.trim(), contactName.trim(), phone.trim(), address.trim())
                                    registering = false
                                    error = null
                                } catch (exception: Exception) {
                                    error = exception.message ?: "Could not submit your request."
                                } finally { busy = false }
                            }
                        }
                    }) { if (busy) CircularProgressIndicator() else Text("Submit account request") }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { registering = false; step = 1; error = null }) { Text("Back to sign in") }
        } else {
            Text("Sign in", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Field("Username", username, { username = it })
            Field("Password", password, { password = it }, password = true)
            Spacer(Modifier.height(12.dp))
            Button(enabled = !busy, onClick = {
                busy = true; error = null; message = null
                scope.launch {
                    try {
                        val result = MobileAuthApi.login(username.trim(), password)
                        onSignedIn(result.account, result.sessionToken)
                    } catch (exception: Exception) {
                        error = exception.message ?: "Could not sign in."
                    } finally { busy = false }
                }
            }) { if (busy) CircularProgressIndicator() else Text("Sign in") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { registering = true; message = null; error = null }) { Text("Create account") }
        }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit,
                  password: Boolean = false, singleLine: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}
