package me.eroi.lolidaily.muzei.ui.screen.pages

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.BangumiApiClient
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.api.link.SourceLinkParserRegistry
import me.eroi.lolidaily.muzei.api.link.isShortLink
import me.eroi.lolidaily.muzei.api.link.resolveShortLink
import me.eroi.lolidaily.muzei.api.link.stripTrackingParams
import me.eroi.lolidaily.muzei.model.SlimCharacter
import me.eroi.lolidaily.muzei.ui.screen.components.FullscreenImageViewer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

private const val TAG = "SubmitPage"
private const val MAX_IMAGE_SIZE = 3L * 1024 * 1024 // 3 MB

/**
 * If [bytes] exceeds [MAX_IMAGE_SIZE], attempts to re-encode as lossy WebP.
 * Returns (compressedBytes, "image/webp") on success, or null if still too large.
 * If [bytes] is already within limit, returns null (no conversion needed).
 */
private fun compressToWebpIfNeeded(bytes: ByteArray): Pair<ByteArray, String>? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    for (quality in listOf(80, 60, 40, 20)) {
        val out = java.io.ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
        val compressed = out.toByteArray()
        if (compressed.size <= MAX_IMAGE_SIZE) {
            bitmap.recycle()
            return compressed to "image/webp"
        }
    }
    bitmap.recycle()
    return null
}


private data class SubmitFormState(
    val imageUri: Uri? = null,
    val imageBytes: ByteArray? = null,
    val imageName: String = "",
    val imageMimeType: String = "",
    val sourceUrl: String = "",
    val artistName: String = "",
    val artistUrl: String = "",
    val characterSearchQuery: String = "",
    val characterSearchResults: List<SlimCharacter> = emptyList(),
    val isSearchingCharacters: Boolean = false,
    val selectedCharacters: List<SlimCharacter> = emptyList(),
    val comment: String = "",
    val selectedTag: String = "LC0",
    val anonymous: Boolean = false,
    val fetchedSourceUrl: String = "",
    val isFetchingImage: Boolean = false,
    val isSubmitting: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
)

/**
 * Image submission page — allows users to upload daily artwork to Loli Commons.
 *
 * Mirrors the JS submitPanel logic from lolicommons.js:
 * - Two-step submit: POST /v1/daily/submit → upload image via presigned URL
 * - Source URL auto-resolve for known platforms (twitter/pixiv/bilibili)
 * - Image validation: jpg/png/webp/avif, ≤ 3 MB
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubmitPage(
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    initialSourceUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val res = context.applicationContext.resources
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SubmitFormState()) }
    var showFullscreenViewer by remember { mutableStateOf(false) }
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                try {
                    val mimeType =
                        context.contentResolver.getType(uri)
                            ?: MimeTypeMap.getSingleton()
                                .getExtensionFromMimeType(context.contentResolver.getType(uri))
                                ?.let { "image/$it" }
                            ?: "image/jpeg"
                    if (mimeType !in listOf("image/jpeg", "image/png", "image/webp", "image/avif")) {
                        withContext(Dispatchers.Main) {
                            state = state.copy(
                                statusMessage = res.getString(R.string.submit_error_format),
                                isError = true,
                            )
                        }
                        return@launch
                    }
                    val bytes =
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@launch
                    if (bytes.size > MAX_IMAGE_SIZE) {
                        val compressed = compressToWebpIfNeeded(bytes)
                        if (compressed != null) {
                            val (cBytes, cMime) = compressed
                            val name = (uri.lastPathSegment ?: "image").replaceAfterLast('.', "webp")
                            withContext(Dispatchers.Main) {
                                state = state.copy(
                                    imageUri = uri,
                                    imageBytes = cBytes,
                                    imageName = name,
                                    imageMimeType = cMime,
                                    statusMessage = res.getString(R.string.submit_compressed_to_webp),
                                    isError = false,
                                )
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                state = state.copy(
                                    statusMessage = res.getString(R.string.submit_error_size),
                                    isError = true,
                                )
                            }
                        }
                        return@launch
                    }
                    val name = uri.lastPathSegment ?: "image.jpg"
                    withContext(Dispatchers.Main) {
                        state = state.copy(
                            imageUri = uri,
                            imageBytes = bytes,
                            imageName = name,
                            imageMimeType = mimeType,
                            statusMessage = null,
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read image", e)
                    withContext(Dispatchers.Main) {
                        state = state.copy(
                            statusMessage = res.getString(R.string.submit_error_read),
                            isError = true,
                        )
                    }
                }
            }
        }

    // Pre-fill source URL from share intent
    LaunchedEffect(initialSourceUrl) {
        if (!initialSourceUrl.isNullOrBlank()) {
            state = state.copy(sourceUrl = initialSourceUrl)
        }
    }

    // Auto-fetch artist + image when source URL changes (debounced)
    LaunchedEffect(state.sourceUrl) {
        val url = state.sourceUrl.trim()
        if (url.isBlank()) {
            if (state.imageBytes != null) {
                state = state.copy(
                    imageUri = null, imageBytes = null, imageName = "", imageMimeType = "",
                    fetchedSourceUrl = "",
                )
            }
            return@LaunchedEffect
        }
        // Resolve short links (b23.tv, t.co, etc.) to actual URLs
        if (isShortLink(url)) {
            val resolved = withContext(Dispatchers.IO) {
                resolveShortLink(url)
            }
            if (resolved != null) {
                state = state.copy(sourceUrl = stripTrackingParams(resolved))
                return@LaunchedEffect  // re-trigger with resolved URL
            }
        }

        val match = SourceLinkParserRegistry.match(url)
        if (match == null) return@LaunchedEffect

        // Canonicalize: mobile → desktop, strip tracking params
        val canonical = SourceLinkParserRegistry.canonicalUrl(url)
        if (canonical != null && canonical != url) {
            state = state.copy(sourceUrl = canonical)
            return@LaunchedEffect  // re-trigger with canonical URL
        }

        // Skip if this exact URL was already fetched
        if (url == state.fetchedSourceUrl) return@LaunchedEffect

        // Debounce: wait for user to stop typing before fetching
        delay(600)

        // Re-check after debounce — URL may have changed
        val currentUrl = state.sourceUrl.trim()
        if (currentUrl != url) return@LaunchedEffect
        val currentMatch = SourceLinkParserRegistry.match(currentUrl)
        if (currentMatch == null) return@LaunchedEffect

        state = state.copy(
            isFetchingImage = true,
            imageUri = null, imageBytes = null, imageName = "", imageMimeType = "",
            artistName = "", artistUrl = "",
        )

        val artistDeferred = async(Dispatchers.IO) {
            SourceLinkParserRegistry.resolveArtist(context, currentMatch.type, currentMatch.resourceId)
        }
        val imageDeferred = async(Dispatchers.IO) {
            LoliApiClient.fetchSourceImage(context, currentUrl)
        }

        val artist = try { artistDeferred.await() } catch (e: Exception) { Log.w(TAG, "resolveArtist failed", e); null }
        val imageResult = try { imageDeferred.await() } catch (_: Exception) { null }

        // Attempt WebP compression if image exceeds size limit
        val finalResult = if (imageResult != null) {
            val (bytes, mime) = imageResult
            if (bytes.size > MAX_IMAGE_SIZE) {
                val compressed = withContext(Dispatchers.IO) { compressToWebpIfNeeded(bytes) }
                if (compressed != null) compressed else null // null = still too large
            } else {
                bytes to mime
            }
        } else null

        withContext(Dispatchers.Main) {
            if (finalResult != null) {
                val (bytes, mime) = finalResult
                state = state.copy(
                    imageUri = "source_image".toUri(),
                    imageBytes = bytes,
                    imageName = "source_${currentMatch.resourceId.replace("/", "_")}.${
                        when (mime) {
                            "image/png" -> "png"
                            "image/webp" -> "webp"
                            "image/avif" -> "avif"
                            else -> "jpg"
                        }
                    }",
                    imageMimeType = mime,
                    isFetchingImage = false,
                    fetchedSourceUrl = currentUrl,
                    artistName = artist?.name ?: state.artistName,
                    artistUrl = artist?.link ?: state.artistUrl,
                    statusMessage = if (imageResult != null && imageResult.first.size > MAX_IMAGE_SIZE)
                        res.getString(R.string.submit_compressed_to_webp) else null,
                )
            } else if (imageResult != null) {
                // Image fetched but too large even after compression
                state = state.copy(
                    isFetchingImage = false,
                    fetchedSourceUrl = currentUrl,
                    artistName = artist?.name ?: state.artistName,
                    artistUrl = artist?.link ?: state.artistUrl,
                    statusMessage = res.getString(R.string.submit_error_size),
                    isError = true,
                )
            } else {
                state = state.copy(
                    isFetchingImage = false,
                    fetchedSourceUrl = currentUrl,
                    artistName = artist?.name ?: state.artistName,
                    artistUrl = artist?.link ?: state.artistUrl,
                    statusMessage = if (artist?.name == null && artist?.link == null)
                        res.getString(R.string.submit_resolve_failed) else null,
                    isError = if (artist?.name == null && artist?.link == null) false else state.isError,
                )
            }
        }
    }
    fun doSubmit() {
        val s = state
        if (s.imageBytes == null) {
            state = state.copy(statusMessage = res.getString(R.string.submit_error_no_image), isError = true)
            return
        }
        if (s.sourceUrl.isBlank()) {
            state = state.copy(statusMessage = res.getString(R.string.submit_error_source_required), isError = true)
            return
        }
        if (s.artistName.isBlank()) {
            state = state.copy(statusMessage = res.getString(R.string.submit_error_artist_required), isError = true)
            return
        }
        if (s.artistUrl.isBlank()) {
            state = state.copy(statusMessage = res.getString(R.string.submit_error_artist_url_required), isError = true)
            return
        }
        // Validate URLs
        try { java.net.URI(s.sourceUrl) } catch (_: Exception) {
            state = state.copy(statusMessage = res.getString(R.string.submit_error_invalid_url, res.getString(R.string.submit_label_source)), isError = true)
            return
        }
        try { java.net.URI(s.artistUrl) } catch (_: Exception) {
            state = state.copy(statusMessage = res.getString(R.string.submit_error_invalid_url, res.getString(R.string.submit_label_artist_url)), isError = true)
            return
        }
        // Character IDs from selected characters
        val characterIds = s.selectedCharacters.map { it.id.toLong() }
        val session = SessionManager.loadSession(context)
        if (session == null || !session.isValid) {
            state = state.copy(statusMessage = res.getString(R.string.submit_error_login), isError = true)
            return
        }
        state = state.copy(isSubmitting = true, statusMessage = null)
        scope.launch(Dispatchers.IO) {
            // Step 1: Submit metadata
            val submitResult =
                LoliApiClient.submitDaily(
                    context = context,
                    sourceUrl = s.sourceUrl.trim(),
                    artistName = s.artistName.trim(),
                    artistUrl = s.artistUrl.trim(),
                    characters = characterIds,
                    tags = s.selectedTag,
                    comment = s.comment.trim(),
                    anonymous = s.anonymous,
                    token = session.token,
                )
            val otc =
                submitResult.getOrElse { e ->
                    withContext(Dispatchers.Main) {
                        state =
                            state.copy(
                                isSubmitting = false,
                                statusMessage = e.message ?: res.getString(R.string.submit_error_generic),
                                isError = true,
                            )
                    }
                    return@launch
                }
            // Step 2: Upload image
            val uploadResult =
                LoliApiClient.uploadDailyImage(
                    context = context,
                    fileName = s.imageName,
                    contentType = s.imageMimeType,
                    contentLength = s.imageBytes.size.toLong(),
                    otc = otc,
                    imageBytes = s.imageBytes,
                    token = session.token,
                )
            withContext(Dispatchers.Main) {
                if (uploadResult.isSuccess) {
                    state =
                        SubmitFormState(
                            statusMessage = res.getString(R.string.submit_success),
                            isError = false,
                        )
                } else {
                    val msg = uploadResult.exceptionOrNull()?.message ?: res.getString(R.string.submit_error_generic)
                    state =
                        state.copy(
                            isSubmitting = false,
                            statusMessage = msg,
                            isError = true,
                        )
                }
            }
        }
    }

    var showClearDialog by remember { mutableStateOf(false) }
    if (showClearDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.submit_clear_title)) },
            text = { Text(stringResource(R.string.submit_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    state = SubmitFormState()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.submit_action_cancel))
                }
            },
        )
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.title_submit)) },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.action_clear_form),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        modifier = modifier,
    ) { padding ->
        if (!isLoggedIn) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.submit_login_required),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onLogin) { Text(stringResource(R.string.action_login)) }
                }
            }
            return@Scaffold
        }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Tag selector ──
            Text(
                text = stringResource(R.string.submit_tag_label),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LC0", "LC YJ", "LC ES").forEach { tag ->
                    FilterChip(
                        selected = state.selectedTag == tag,
                        onClick = { state = state.copy(selectedTag = tag) },
                        label = { Text(tag) },
                        leadingIcon =
                            if (state.selectedTag == tag) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                    )
                }
            }

            // ── Image picker ──
            Text(
                text = stringResource(R.string.submit_image_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 2.dp,
                            color =
                                if (state.imageUri != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            shape = RoundedCornerShape(12.dp),
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable(enabled = !state.isFetchingImage && !state.isSubmitting) {
                            if (state.imageBytes != null) {
                                showFullscreenViewer = true
                            } else {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (state.imageUri != null) {
                    AsyncImage(
                        model = state.imageBytes ?: state.imageUri,
                        contentDescription = stringResource(R.string.submit_image_preview),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    // Clear button
                    IconButton(
                        onClick = {
                            state =
                                state.copy(
                                    imageUri = null,
                                    imageBytes = null,
                                    imageName = "",
                                    imageMimeType = "",
                                )
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.content_desc_clear),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (state.isFetchingImage) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.submit_fetching_image),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.submit_image_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.submit_image_formats),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            // ── Image info ──
            if (state.imageBytes != null) {
                val imageInfo = remember(state.imageBytes) {
                    val bytes = state.imageBytes!!
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    val w = opts.outWidth
                    val h = opts.outHeight
                    val format = when {
                        state.imageMimeType.contains("png") -> "PNG"
                        state.imageMimeType.contains("webp") -> "WebP"
                        state.imageMimeType.contains("avif") -> "AVIF"
                        else -> "JPEG"
                    }
                    val sizeStr = when {
                        bytes.size >= 1_048_576 -> "%.1f MB".format(bytes.size / 1_048_576.0)
                        else -> "%.0f KB".format(bytes.size / 1024.0)
                    }
                    Triple(format, "${w} × ${h}", sizeStr)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = imageInfo.first,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = imageInfo.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = imageInfo.third,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Form fields ──
            OutlinedTextField(
                value = state.sourceUrl,
                onValueChange = { state = state.copy(sourceUrl = it) },
                label = {
                    Text(
                        stringResource(R.string.submit_label_source) + " *",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                placeholder = { Text(stringResource(R.string.submit_hint_source)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
            )

            OutlinedTextField(
                value = state.artistName,
                onValueChange = { state = state.copy(artistName = it) },
                label = {
                    Text(
                        stringResource(R.string.submit_label_artist) + " *",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                placeholder = { Text(stringResource(R.string.submit_hint_artist)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
            )

            OutlinedTextField(
                value = state.artistUrl,
                onValueChange = { state = state.copy(artistUrl = it) },
                label = {
                    Text(
                        stringResource(R.string.submit_label_artist_url) + " *",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                placeholder = { Text(stringResource(R.string.submit_hint_artist_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
            )

            // ── Character search ──
            Text(
                text = stringResource(R.string.submit_label_characters),
                style = MaterialTheme.typography.labelLarge,
            )

            // Debounced character search
            var searchJob by remember { mutableStateOf<Job?>(null) }
            LaunchedEffect(state.characterSearchQuery) {
                val query = state.characterSearchQuery.trim()
                if (query.length < 2) {
                    state = state.copy(characterSearchResults = emptyList(), isSearchingCharacters = false)
                    return@LaunchedEffect
                }
                state = state.copy(isSearchingCharacters = true)
                searchJob?.cancel()
                searchJob = launch(Dispatchers.IO) {
                    delay(400)
                    val results = BangumiApiClient.searchCharacters(context, query)
                    withContext(Dispatchers.Main) {
                        state = state.copy(
                            characterSearchResults = results.filter { r -> state.selectedCharacters.none { it.id == r.id } },
                            isSearchingCharacters = false,
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.characterSearchQuery,
                    onValueChange = {
                        state = state.copy(characterSearchQuery = it)
                    },
                    placeholder = { Text(stringResource(R.string.submit_hint_character_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.isSearchingCharacters) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                )

                DropdownMenu(
                    expanded = state.characterSearchResults.isNotEmpty() && state.characterSearchQuery.trim().length >= 2,
                    onDismissRequest = { state = state.copy(characterSearchResults = emptyList()) },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                ) {
                    state.characterSearchResults.forEach { character ->
                        val displayName = if (character.nameCN.isNotBlank()) "${character.nameCN} (${character.name})" else character.name
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    character.images?.let { imgs ->
                                        AsyncImage(
                                            model = imgs.grid.ifBlank { imgs.medium.ifBlank { imgs.small } },
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(displayName, style = MaterialTheme.typography.bodyMedium)
                                }
                            },
                            onClick = {
                                state = state.copy(
                                    selectedCharacters = state.selectedCharacters + character,
                                    characterSearchQuery = "",
                                    characterSearchResults = emptyList(),
                                )
                            },
                        )
                    }
                }
            }

            // Selected character chips
            if (state.selectedCharacters.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.selectedCharacters.forEach { character ->
                        val chipLabel = character.nameCN.ifBlank { character.name }
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(chipLabel) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        state = state.copy(
                                            selectedCharacters = state.selectedCharacters - character,
                                        )
                                    },
                                    modifier = Modifier.size(18.dp),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.comment,
                onValueChange = { state = state.copy(comment = it) },
                label = { Text(stringResource(R.string.submit_label_comment)) },
                placeholder = { Text(stringResource(R.string.submit_hint_comment)) },
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
            )

            // ── Anonymous toggle ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !state.isSubmitting) {
                            state = state.copy(anonymous = !state.anonymous)
                        }
                        .padding(horizontal = 4.dp),
            ) {
                Checkbox(
                    checked = state.anonymous,
                    onCheckedChange = { state = state.copy(anonymous = it) },
                    enabled = !state.isSubmitting,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.submit_anonymous),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // ── Submit notice link ──
            TextButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.data = "https://bgm.tv/group/topic/417120".toUri()
                    context.startActivity(intent)
                },
            ) {
                Text(stringResource(R.string.submit_guidelines_link))
            }

            // ── Submit button + progress ──
            if (state.isSubmitting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
            }
            Button(
                onClick = { doSubmit() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !state.isSubmitting,
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.submit_sending))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.submit_button))
                }
            }

            // ── Status message ──
            AnimatedVisibility(visible = state.statusMessage != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = state.statusMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (state.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showFullscreenViewer && state.imageBytes != null) {
        FullscreenImageViewer(
            model = state.imageBytes!!,
            filename = state.imageName.ifEmpty { "image" },
            onDismiss = { showFullscreenViewer = false },
        )
    }
}
