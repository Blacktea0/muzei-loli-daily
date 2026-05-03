package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountCard(
    isLoggedIn: Boolean,
    bgmDomain: String,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDomainChanged: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLoggedIn) "Logged in to Bangumi" else "Not logged in",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (isLoggedIn) "via $bgmDomain" else "Login to react to images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoggedIn) {
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("Logout")
                }
            } else {
                var showDomainPicker by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { showDomainPicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Login")
                    }
                }

                if (showDomainPicker) {
                    DomainPickerDialog(
                        currentDomain = bgmDomain,
                        onDomainSelected = { domain ->
                            showDomainPicker = false
                            onDomainChanged(domain)
                            onLogin()
                        },
                        onDismiss = { showDomainPicker = false },
                    )
                }
            }
        }
    }
}

@Composable
fun DomainPickerDialog(
    currentDomain: String,
    onDomainSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val domains = listOf("chii.in", "bgm.tv", "bangumi.tv")
    var selectedDomain by remember { mutableStateOf(currentDomain) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Choose login site",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                domains.forEach { domain ->
                    val selected = domain == selectedDomain
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { selectedDomain = domain }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(domain, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    FilledTonalButton(onClick = { onDomainSelected(selectedDomain) }) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}
