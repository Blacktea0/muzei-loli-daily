package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.eroi.lolidaily.muzei.R


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
                    text = stringResource(R.string.title_choose_login_site),
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
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    FilledTonalButton(onClick = { onDomainSelected(selectedDomain) }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
            }
        }
    }
}
