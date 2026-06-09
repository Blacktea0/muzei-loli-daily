package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.BangumiApiClient
import me.eroi.lolidaily.muzei.model.SlimCharacter

/**
 * State holder for [CharacterSearchBar].
 * Create with [rememberCharacterSearchBarState].
 */
@OptIn(ExperimentalMaterial3Api::class)
class CharacterSearchBarState internal constructor(
    val searchBarState: SearchBarState,
) {
    suspend fun animateToExpanded() = searchBarState.animateToExpanded()
    suspend fun animateToCollapsed() = searchBarState.animateToCollapsed()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCharacterSearchBarState(): CharacterSearchBarState {
    val searchBarState = rememberContainedSearchBarState()
    return remember(searchBarState) { CharacterSearchBarState(searchBarState) }
}

/**
 * Full-screen character search bar with autocomplete and infinite-scroll pagination.
 *
 * @param selectedCharacters already selected characters (excluded from results)
 * @param onCharacterSelected called when user taps a search result
 * @param state state holder — use [rememberCharacterSearchBarState] to create
 * @param modifier modifier for the outer [ExpandedFullScreenContainedSearchBar]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSearchBar(
    selectedCharacters: List<SlimCharacter>,
    onCharacterSelected: (SlimCharacter) -> Unit,
    modifier: Modifier = Modifier,
    state: CharacterSearchBarState = rememberCharacterSearchBarState(),
) {
    val scope = rememberCoroutineScope()
    val textFieldState = rememberTextFieldState()
    val searchBarState = state.searchBarState

    var searchJob by remember { mutableStateOf<Job?>(null) }
    var scrollId by remember { mutableStateOf<String?>(null) }
    var searchTotal by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SlimCharacter>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var autocompleteJob by remember { mutableStateOf<Job?>(null) }

    // Debounced autocomplete
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { query ->
                autocompleteJob?.cancel()
                val trimmed = query.trim()
                if (trimmed.isEmpty()) {
                    suggestions = emptyList()
                    return@collect
                }
                autocompleteJob = launch(Dispatchers.IO) {
                    delay(300)
                    val s = BangumiApiClient.autocompleteCharacters(trimmed)
                    withContext(Dispatchers.Main) { suggestions = s }
                }
            }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = emptyList(); isSearching = false
            scrollId = null; searchTotal = 0; suggestions = emptyList()
            return
        }
        suggestions = emptyList()
        isSearching = true
        searchJob?.cancel()
        searchJob = scope.launch(Dispatchers.IO) {
            val page = BangumiApiClient.searchCharacters(trimmed)
            withContext(Dispatchers.Main) {
                results = page.characters.filter { r -> selectedCharacters.none { it.id == r.id } }
                scrollId = page.scrollId; searchTotal = page.total; isSearching = false
            }
        }
    }

    fun loadMore() {
        val sid = scrollId ?: return
        if (isLoadingMore) return
        isLoadingMore = true
        scope.launch(Dispatchers.IO) {
            val page = BangumiApiClient.searchCharactersNextPage(sid)
            withContext(Dispatchers.Main) {
                results = results + page.characters.filter { r -> selectedCharacters.none { it.id == r.id } }
                scrollId = page.scrollId; searchTotal = page.total; isLoadingMore = false
            }
        }
    }

    ExpandedFullScreenContainedSearchBar(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { search(textFieldState.text.toString()) },
                placeholder = {
                    Text(
                        modifier = Modifier.clearAndSetSemantics {},
                        text = stringResource(R.string.submit_hint_character_search),
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (isSearching) {
                        LoadingIndicator(modifier = Modifier.size(32.dp))
                    } else if (textFieldState.text.isNotEmpty()) {
                        IconButton(onClick = {
                            textFieldState.setTextAndPlaceCursorAtEnd("")
                            results = emptyList()
                            scrollId = null
                            searchTotal = 0
                            suggestions = emptyList()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.content_desc_clear))
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) {
        HorizontalDivider()
        // Autocomplete suggestions
        if (suggestions.isNotEmpty() && results.isEmpty() && !isSearching) {
            Column(modifier = Modifier.fillMaxWidth()) {
                suggestions.forEach { suggestion ->
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                textFieldState.setTextAndPlaceCursorAtEnd(suggestion)
                                search(suggestion)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        // Search results
        if (results.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { character ->
                    val displayName = if (character.nameCN.isNotBlank()) "${character.name} (${character.nameCN})" else character.name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCharacterSelected(character)
                                results = emptyList()
                                textFieldState.setTextAndPlaceCursorAtEnd("")
                                scope.launch { searchBarState.animateToCollapsed() }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        val imgs = character.images
                        if (imgs != null) {
                            AsyncImage(
                                model = imgs.small,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Spacer(modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (scrollId != null) {
                    item {
                        LaunchedEffect(Unit) { loadMore() }
                        if (isLoadingMore) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator(modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }
            }
        }

        // Total count
        if (searchTotal > 0 && !isSearching) {
            Text(
                text = "${results.size} / $searchTotal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
