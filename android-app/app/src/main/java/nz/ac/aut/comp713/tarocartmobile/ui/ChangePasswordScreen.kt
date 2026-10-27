package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nz.ac.aut.comp713.tarocartmobile.data.MobileApiException
import nz.ac.aut.comp713.tarocartmobile.data.MobileAuthApi

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChangePasswordScreen(
    sessionToken: String,
    onBack: () -> Unit,
    onSessionEnded: (String) -> Unit
) {
    // Passwords stay in Compose memory only; they are not saved in Android instance state.
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler { if (!saving) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change password") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !saving) { Text("Back") }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Changing your password signs out all sessions for your account. Sign in again with your new password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it; error = null },
                label = { Text("Current password") },
                singleLine = true,
                enabled = !saving,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { if (it.length <= 72) { newPassword = it; error = null } },
                label = { Text("New password") },
                supportingText = { Text("12 to 72 characters") },
                singleLine = true,
                enabled = !saving,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { if (it.length <= 72) { confirmPassword = it; error = null } },
                label = { Text("Confirm new password") },
                singleLine = true,
                enabled = !saving,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                onClick = {
                    error = when {
                        currentPassword.isBlank() -> "Enter your current password."
                        newPassword.length !in 12..72 -> "New password must be 12 to 72 characters."
                        confirmPassword.isBlank() -> "Confirm your new password."
                        newPassword != confirmPassword -> "New passwords do not match."
                        else -> null
                    }
                    if (error != null) return@Button
                    saving = true
                    scope.launch {
                        try {
                            MobileAuthApi.changePassword(sessionToken, currentPassword,
                                newPassword, confirmPassword)
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                            onSessionEnded("Password changed. Sign in with your new password.")
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: MobileApiException) {
                            if (exception.statusCode == 401) {
                                onSessionEnded("Your session has expired. Sign in again before changing your password.")
                            } else {
                                error = MobileAuthApi.userFacingMessage(exception,
                                    "Password could not be changed. Please try again.")
                            }
                        } catch (exception: Exception) {
                            error = MobileAuthApi.userFacingMessage(exception,
                                "Password could not be changed. Please try again.")
                        } finally {
                            saving = false
                        }
                    }
                }
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp) else Text("Change password")
            }
        }
    }
}
