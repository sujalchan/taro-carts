package nz.ac.aut.comp713.tarocartmobile.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

    Column(Modifier.fillMaxSize()
        .background(Color(0xFFF4F6F6))
        .statusBarsPadding()
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
                    Spacer(Modifier.height(8.dp))
                    TextButtonLike("←  Account details", onClick = { step = 2 })
                    Spacer(Modifier.height(8.dp))
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
                  password: Boolean = false, singleLine: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}
