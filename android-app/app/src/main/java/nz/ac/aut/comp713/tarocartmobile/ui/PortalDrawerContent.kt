package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PortalDrawerContent(
    title: String,
    username: String,
    role: String,
    onSignOut: suspend () -> Unit,
    onEditInformation: (() -> Unit)? = null,
    onChangePassword: (() -> Unit)? = null
) {
    var signingOut by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
            PortalHero(role, title, "Signed in as $username")
            Text("MY ACCOUNT", modifier = Modifier.padding(top = 26.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        if (onEditInformation != null) {
            NavigationDrawerItem(
                label = { Text("Edit information") },
                selected = false,
                modifier = Modifier.padding(horizontal = 12.dp),
                onClick = onEditInformation
            )
        }
        if (onChangePassword != null) {
            NavigationDrawerItem(
                label = { Text("Change password") },
                selected = false,
                modifier = Modifier.padding(horizontal = 12.dp),
                onClick = onChangePassword
            )
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            OutlinedButton(
                enabled = !signingOut,
                modifier = Modifier.padding(top = 12.dp),
                onClick = {
                    signOutError = null
                    signingOut = true
                    scope.launch {
                        try {
                            onSignOut()
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            signOutError = exception.message ?: "Could not sign out. Check your connection and retry."
                        } finally {
                            signingOut = false
                        }
                    }
                }
            ) {
                if (signingOut) CircularProgressIndicator() else Text("Sign out")
            }
            signOutError?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
