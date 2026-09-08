package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.BangumiApiClient
import me.eroi.lolidaily.muzei.model.SlimCharacter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun parseBangumiCharacterId(input: String): Int? {
    val trimmed = input.trim()
    if (trimmed.matches(Regex("[0-9]+"))) {
        return trimmed.toIntOrNull()?.takeIf { it > 0 }
    }
    val url = trimmed.toHttpUrlOrNull() ?: return null
    if (url.host !in setOf("bgm.tv", "bangumi.tv", "chii.in")) return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    val match = Regex("/character/([0-9]+)/?").matchEntire(url.encodedPath) ?: return null
    return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
}

internal fun parseBangumiCharacterIds(input: String): List<Int>? {
    if (input.isBlank()) return null
    return input.trim().split(Regex("[\\s\\p{Z}]+"))
        .map { parseBangumiCharacterId(it) ?: return null }
        .distinct()
}

@Composable
fun CharacterUrlDialog(
    selectedCharacters: List<SlimCharacter>,
    onCharacterSelected: (SlimCharacter) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun addCharacters() {
        if (isLoading) return
        val characterIds = parseBangumiCharacterIds(input)
        if (characterIds == null) {
            errorRes = R.string.submit_character_url_invalid
            return
        }
        val selectedIds = selectedCharacters.map { it.id }.toSet()
        val pendingIds = characterIds.filterNot { it in selectedIds }
        if (pendingIds.isEmpty()) {
            errorRes = R.string.submit_character_already_added
            return
        }
        errorRes = null
        isLoading = true
        scope.launch {
            val failedIds = mutableListOf<Int>()
            for (characterId in pendingIds) {
                val character = withContext(Dispatchers.IO) {
                    BangumiApiClient.fetchCharacterDetail(characterId)
                }
                if (character == null || (character.name.isBlank() && character.nameCN.isBlank())) {
                    failedIds.add(characterId)
                } else {
                    onCharacterSelected(character)
                }
            }
            isLoading = false
            if (failedIds.isNotEmpty()) {
                input = failedIds.joinToString(" ")
                errorRes = R.string.submit_character_fetch_failed
            } else {
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.submit_character_url_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        errorRes = null
                    },
                    label = { Text(stringResource(R.string.submit_character_url_label)) },
                    placeholder = { Text("123 https://bgm.tv/character/456") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isLoading,
                    isError = errorRes != null,
                    supportingText = {
                        Text(stringResource(errorRes ?: R.string.submit_character_url_hint))
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { addCharacters() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { addCharacters() }, enabled = input.isNotBlank() && !isLoading) {
                if (isLoading) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(stringResource(R.string.submit_action_add_character))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
