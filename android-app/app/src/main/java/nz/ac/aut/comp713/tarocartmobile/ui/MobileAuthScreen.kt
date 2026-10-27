package nz.ac.aut.comp713.tarocartmobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.kotlin.awaitFetchPlace
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import nz.ac.aut.comp713.tarocartmobile.BuildConfig
import nz.ac.aut.comp713.tarocartmobile.data.CurrentLocationAddress
import nz.ac.aut.comp713.tarocartmobile.data.MobileAccount
import nz.ac.aut.comp713.tarocartmobile.data.MobileAuthApi

@Composable
fun MobileAuthScreen(
    account: MobileAccount?,
    signInMessage: String? = null,
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
    var addressSelected by remember { mutableStateOf(false) }
    var suggestedAddress by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val useCurrentLocation: () -> Unit = {
        locating = true
        addressError = null
        suggestedAddress = null
        scope.launch {
            try {
                suggestedAddress = CurrentLocationAddress.lookup(context)
                addressError = null
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
            addressError = "Precise location is needed for a street address. Tap Use current location again and choose Change to precise location."
        } else {
            addressError = "Location access was denied. Allow precise location, or search for your address instead."
        }
    }
    val addressSearch = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == PlaceAutocompleteActivity.RESULT_CANCELED) {
            return@rememberLauncherForActivityResult
        }
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
        val sessionToken = PlaceAutocomplete.getSessionTokenFromIntent(intent)
        scope.launch {
            try {
                val place = Places.createClient(context).awaitFetchPlace(
                    prediction.placeId,
                    listOf(Place.Field.FORMATTED_ADDRESS, Place.Field.ADDRESS_COMPONENTS)
                ) { this.sessionToken = sessionToken }.place
                val selectedAddress = place.formattedAddress
                val components = place.addressComponents?.asList().orEmpty()
                val types = components.flatMap { it.types }.toSet()
                val country = components.firstOrNull { "country" in it.types }?.shortName
                val hasLocality = types.any {
                    it == "locality" || it == "postal_town" ||
                        it == "sublocality_level_1" || it == "postal_code"
                }
                if (selectedAddress.isNullOrBlank() || selectedAddress.length > 500 ||
                    "street_number" !in types || "route" !in types || !hasLocality || country != "NZ") {
                    addressError = "Select a complete New Zealand street address, including its street number."
                } else {
                    address = selectedAddress
                    addressSelected = true
                    suggestedAddress = null
                    addressError = null
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
                .setCountries(listOf("NZ"))
                .build(context))
        } catch (exception: Exception) {
            addressError = exception.message ?: "Could not open address search."
        }
    }

    Column(Modifier.fillMaxSize()
        .background(Color(0xFFF4F6F6))
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .background(Color.White, RoundedCornerShape(20.dp))
        .border(1.dp, Color(0xFFD7DED5), RoundedCornerShape(20.dp))
        .padding(24.dp),
        verticalArrangement = if (registering) Arrangement.Top else Arrangement.Center) {
        Text("Taro Carts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            Spacer(Modifier.height(22.dp))
            RegistrationProgress(step = step, role = role)
            Spacer(Modifier.height(26.dp))
            Text("STEP $step OF 3", style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF17665F), fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp)
            Spacer(Modifier.height(7.dp))
            Text(
                when (step) {
                    1 -> "How will you use Taro Carts?"
                    2 -> "Create a ${role.lowercase()} account"
                    else -> if (role == "CUSTOMER") "Customer details" else "Dispatcher details"
                },
                style = if (step == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontFamily = if (step == 1) FontFamily.Serif else FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (step) {
                    1 -> "Choose your account type. We’ll guide you through the details next."
                    2 -> "Choose the details you’ll use to sign in after approval."
                    else -> if (role == "CUSTOMER") "Tell us about the business receiving your taro allocations."
                        else "Tell us who will be delivering the assigned allocations."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            when (step) {
                1 -> {
                    RoleCard(
                        title = "Customer",
                        description = "Request weekly taro allocations and follow your orders.",
                        action = "Continue as customer",
                        symbol = "◈",
                        selected = role == "CUSTOMER",
                        tint = Color(0xFF18564C),
                        container = Color(0xFFF4F9F4),
                        onClick = { role = "CUSTOMER" }
                    )
                    Spacer(Modifier.height(14.dp))
                    RoleCard(
                        title = "Dispatcher",
                        description = "See assigned deliveries and plan your work.",
                        action = "Continue as dispatcher",
                        symbol = "↗",
                        selected = role == "DISPATCHER",
                        tint = Color(0xFF244C68),
                        container = Color(0xFFF1F6FA),
                        onClick = { role = "DISPATCHER" }
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Every request is reviewed before an account can be used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17665F))) {
                        Text("Continue  →")
                    }
                }
                2 -> {
                    Field("Username", username, { username = it })
                    Field("Password (at least 12 characters)", password, { password = it }, password = true)
                    Field("Confirm password", confirmPassword, { confirmPassword = it }, password = true)
                    Spacer(Modifier.height(8.dp))
                    TextButtonLike("←  Account type", onClick = { step = 1 })
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        error = when {
                            username.length < 3 -> "Username must be at least 3 characters."
                            password.length < 12 -> "Password must be at least 12 characters."
                            password != confirmPassword -> "Passwords do not match."
                            else -> null
                        }
                        if (error == null) step = 3
                    }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17665F))) { Text("Continue  →") }
                }
                else -> {
                    Field(if (role == "CUSTOMER") "Business name *" else "Dispatcher name *",
                        name, { name = it })
                    if (role == "CUSTOMER") {
                        Field("Contact name (optional)", contactName, { contactName = it })
                    }
                    Field("Phone number (optional)", phone, { phone = it },
                        keyboardType = KeyboardType.Phone)
                    Spacer(Modifier.height(10.dp))
                    Text("Street address *", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Choose the address where deliveries should go.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    if (BuildConfig.PLACES_API_KEY.isNotBlank()) {
                        Card(
                            onClick = openAddressSearch,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF8)),
                            border = BorderStroke(1.dp,
                                if (addressSelected) Color(0xFF17665F)
                                else MaterialTheme.colorScheme.outline)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (addressSelected) "Selected address" else "Search for an address",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF17665F), fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(if (address.isBlank()) "Tap to search New Zealand streets" else address,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (address.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(if (addressSelected) "Change" else "Search",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF17665F), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Field("Street address", address, {
                            if (it.length <= 500) {
                                address = it
                                addressSelected = false
                                suggestedAddress = null
                            }
                        }, singleLine = false)
                        Text("Address search is unavailable. Enter your street address manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = !locating && !busy, onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                            useCurrentLocation()
                        } else {
                            locationPermission.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    }) {
                        if (locating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Finding nearby address...")
                        } else {
                            Text("Use my current location")
                        }
                    }
                    addressError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    suggestedAddress?.let { suggestion ->
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F9F4)),
                            border = BorderStroke(1.dp, Color(0xFF9BC8AE))) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Is this your address?", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(suggestion, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                Text("Check the street number and suburb before using this nearby address.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = {
                                    address = suggestion
                                    addressSelected = true
                                    suggestedAddress = null
                                    addressError = null
                                }, modifier = Modifier.fillMaxWidth()) { Text("Use this address") }
                                TextButtonLike("Not my address", onClick = { suggestedAddress = null })
                            }
                        }
                    }
                    if (addressSelected) {
                        Spacer(Modifier.height(8.dp))
                        Text("Check that this is the correct street address before submitting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButtonLike("←  Account details", onClick = { step = 2 })
                    Spacer(Modifier.height(8.dp))
                    Button(enabled = !busy && !locating, onClick = {
                        error = when {
                            name.isBlank() || address.isBlank() -> "Name and address are required."
                            BuildConfig.PLACES_API_KEY.isNotBlank() && !addressSelected ->
                                "Select an address from Google Places suggestions."
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
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: Exception) {
                                    error = MobileAuthApi.userFacingMessage(exception,
                                        "We couldn't submit your request. Check your connection and try again.")
                                } finally { busy = false }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17665F))) {
                        if (busy) CircularProgressIndicator() else Text("Request an account  →")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            TextButtonLike("Back to sign in", onClick = { registering = false; step = 1; error = null })
        } else {
            Text("Sign in", style = MaterialTheme.typography.titleLarge)
            signInMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
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
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        error = MobileAuthApi.userFacingMessage(exception,
                            "We couldn't sign you in. Check your details and try again.")
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
private fun RegistrationProgress(step: Int, role: String) {
    val labels = listOf("Account type", "Account details", if (role == "CUSTOMER") "Customer details" else "Dispatcher details")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val current = index + 1 == step
            val complete = index + 1 < step
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.height(26.dp).width(26.dp),
                        shape = CircleShape,
                        color = if (current) Color(0xFF17665F) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (complete) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        BoxCenterText(if (complete) "✓" else "${index + 1}")
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) Color(0xFF17665F) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 2.dp,
                    color = if (current || complete) Color(0xFF17665F) else MaterialTheme.colorScheme.outlineVariant)
            }
            if (index < labels.lastIndex) Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun BoxCenterText(text: String) {
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    action: String,
    symbol: String,
    selected: Boolean,
    tint: Color,
    container: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) tint else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Surface(shape = RoundedCornerShape(11.dp), color = if (title == "Customer") Color(0xFFE0EEE3) else Color(0xFFDAE9F2)) {
                androidx.compose.foundation.layout.Box(Modifier.height(43.dp).width(43.dp), contentAlignment = Alignment.Center) {
                    Text(symbol, color = tint, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(15.dp))
            Text(title, color = tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Text("$action  →", color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit,
                  password: Boolean = false, singleLine: Boolean = true,
                  keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}
