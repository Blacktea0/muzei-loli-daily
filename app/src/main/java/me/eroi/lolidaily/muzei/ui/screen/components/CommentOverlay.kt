package me.eroi.lolidaily.muzei.ui.screen.components

import android.view.WindowManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import coil3.compose.AsyncImagePainter
import androidx.compose.ui.res.stringResource
import me.eroi.lolidaily.muzei.R
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.imageLoader
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import me.eroi.lolidaily.muzei.api.SessionManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun CommentBottomSheet(
    onDismiss: () -> Unit,
    onPostReply: (String, (Boolean) -> Unit) -> Unit,
    initialText: String = "",
) {
    var isPosting by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val focusRequester = remember { FocusRequester() }

    val parsedInitial = remember(initialText) {
        val hasQuote = initialText.startsWith("[quote]")
        val closeIndex = if (hasQuote) initialText.indexOf("[/quote]") else -1
        if (hasQuote && closeIndex != -1) {
            val qTag = "[/quote]"
            val prefix = initialText.substring(0, closeIndex + qTag.length) + "\n"
            val nickname =
                Regex("\\[b](.*?)\\[/b]").find(prefix)?.groupValues?.getOrNull(1) ?: "Loli"
            val quoteContent =
                Regex("说:\\s*([\\s\\S]*?)\\[/quote]").find(prefix)?.groupValues?.getOrNull(1)
                    ?: ""
            val remaining = initialText.substring(closeIndex + qTag.length).trimStart('\n')
            Triple(prefix, nickname to quoteContent, remaining)
        } else {
            Triple("", null, initialText)
        }
    }

    val initialDisplayQuote = parsedInitial.second?.let { (nickname, quoteContent) ->
        stringResource(R.string.comment_label_reply_to, nickname, quoteContent).replace('\n', ' ')
    }

    var quotePrefix by remember(parsedInitial) { mutableStateOf(parsedInitial.first) }
    var displayQuote by remember(parsedInitial) { mutableStateOf(initialDisplayQuote) }
    var textFieldValue by remember(parsedInitial) {
        mutableStateOf(
            TextFieldValue(
                text = parsedInitial.third,
                selection = TextRange(parsedInitial.third.length)
            )
        )
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var hasImeStarted by remember { mutableStateOf(imeBottom > 0) }
    var showPanelFallback by remember { mutableStateOf(false) }
    val showPanel = hasImeStarted || showPanelFallback

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) {
            hasImeStarted = true
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
        delay(500.milliseconds)
        showPanelFallback = true
    }

    Dialog(
        onDismissRequest = {
            if (!isPosting) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            val previousSoftInputMode = dialogWindow?.attributes?.softInputMode
            dialogWindow?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            onDispose {
                if (previousSoftInputMode != null) {
                    dialogWindow.setSoftInputMode(previousSoftInputMode)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(enabled = !isPosting) { onDismiss() }
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .alpha(if (showPanel) 1f else 0f)
                    .windowInsetsPadding(
                        WindowInsets.ime
                            .union(WindowInsets.navigationBars)
                            .only(WindowInsetsSides.Bottom)
                    ),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.comment_title_post),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss, enabled = !isPosting) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            if (isPosting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        val content = textFieldValue.text
                                        if (content.isNotBlank()) {
                                            isPosting = true
                                            val finalContent =
                                                if (quotePrefix.isNotEmpty()) "$quotePrefix$content" else content
                                            onPostReply(finalContent) { success ->
                                                isPosting = false
                                                if (success) {
                                                    onDismiss()
                                                }
                                            }
                                        }
                                    },
                                    enabled = textFieldValue.text.isNotBlank()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = stringResource(R.string.comment_action_send)
                                    )
                                }
                            }
                        }
                    }

                    // Display Quote reference block above the input box if replying
                    if (displayQuote != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = displayQuote!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        quotePrefix = ""
                                        displayQuote = null
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.comment_action_cancel_reply),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Input Field (Auto-grows, max 4 lines)
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        placeholder = { Text(stringResource(R.string.comment_hint_say_something)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        enabled = !isPosting,
                        maxLines = 4,
                    )

                    // BBCode & Emoji Tools Row (Horizontally scrollable)
                    val toolRowScrollState = rememberScrollState()
                    LaunchedEffect(isExpanded) {
                        if (isExpanded) {
                            delay(100)
                            toolRowScrollState.animateScrollTo(toolRowScrollState.maxValue)
                        } else {
                            toolRowScrollState.animateScrollTo(0)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(toolRowScrollState),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val insertBBCode = { open: String, close: String ->
                            val text = textFieldValue.text
                            val selection = textFieldValue.selection
                            val start = selection.min
                            val end = selection.max
                            val selectedText = text.substring(start, end)
                            val newMiddle = "$open$selectedText$close"
                            val newText = text.replaceRange(start, end, newMiddle)
                            val newCursorPos =
                                if (start == end) start + open.length else start + newMiddle.length

                            textFieldValue = TextFieldValue(
                                text = newText,
                                selection = TextRange(newCursorPos)
                            )
                        }

                        // Emoji Toggle Button
                        IconButton(
                            onClick = {
                                showEmojiPanel = !showEmojiPanel
                                if (showEmojiPanel) {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showEmojiPanel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mood,
                                contentDescription = stringResource(R.string.comment_editor_emoji),
                                modifier = Modifier.size(20.dp),
                                tint = if (showEmojiPanel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        BBCodeButton(
                            icon = Icons.Default.FormatBold,
                            label = stringResource(R.string.comment_editor_bold),
                            onClick = { insertBBCode("[b]", "[/b]") }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        BBCodeButton(
                            icon = Icons.Default.FormatItalic,
                            label = stringResource(R.string.comment_editor_italic),
                            onClick = { insertBBCode("[i]", "[/i]") }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        BBCodeButton(
                            icon = Icons.Default.FormatUnderlined,
                            label = stringResource(R.string.comment_editor_underline),
                            onClick = { insertBBCode("[u]", "[/u]") }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        BBCodeButton(
                            icon = Icons.Default.StrikethroughS,
                            label = stringResource(R.string.comment_editor_strikethrough),
                            onClick = { insertBBCode("[s]", "[/s]") }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        BBCodeButton(
                            icon = Icons.Default.VisibilityOff,
                            label = stringResource(R.string.comment_editor_spoiler),
                            onClick = { insertBBCode("[mask]", "[/mask]") }
                        )

                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(8.dp))
                                BBCodeButton(
                                    icon = Icons.Default.Link,
                                    label = stringResource(R.string.comment_editor_link),
                                    onClick = { insertBBCode("[url=]", "[/url]") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BBCodeButton(
                                    icon = Icons.Default.Image,
                                    label = stringResource(R.string.comment_editor_image),
                                    onClick = { insertBBCode("[img]", "[/img]") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BBCodeButton(
                                    icon = Icons.Default.Code,
                                    label = stringResource(R.string.comment_editor_code),
                                    onClick = { insertBBCode("[code]", "[/code]") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        BBCodeButton(
                            icon = if (isExpanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                            label = if (isExpanded) stringResource(R.string.comment_editor_collapse) else stringResource(R.string.comment_editor_expand),
                            onClick = { isExpanded = !isExpanded }
                        )
                    }

                    // Vertically scrollable Emoji Panel
                    if (showEmojiPanel) {
                        EmojiPanel(
                            onEmojiSelect = { emojiCode ->
                                val text = textFieldValue.text
                                val selection = textFieldValue.selection
                                val start = selection.min
                                val end = selection.max
                                val newText = text.replaceRange(start, end, emojiCode)
                                val newCursorPos = start + emojiCode.length

                                textFieldValue = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(newCursorPos)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BBCodeButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmojiPanel(
    onEmojiSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val bgmDomain = remember { SessionManager.loadDomain(context).ifBlank { "chii.in" } }
    val categories = SmileyMapper.categories
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedCategory = categories[selectedTab]

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Tab Row
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            divider = {}
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    icon = {
                        RetryableEmojiImage(
                            url = SmileyMapper.resolveUrl(category.iconCode, bgmDomain).orEmpty(),
                            contentDescription = category.name,
                            isPixelArt = category.isPixelArt,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            }
        }

        val isPixelArt = selectedCategory.isPixelArt
        val emojiSize = if (isPixelArt) 24.dp else 40.dp

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidth = maxWidth
            val cols = if (isPixelArt) {
                (containerWidth.value / 48).toInt().coerceAtLeast(8)
            } else {
                (containerWidth.value / 60).toInt().coerceAtLeast(6)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(selectedCategory.codes) { code ->
                    val emojiTextCode = SmileyMapper.textCode(code)
                    val url = SmileyMapper.resolveUrl(code, bgmDomain).orEmpty()

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onEmojiSelect(emojiTextCode) },
                        contentAlignment = Alignment.Center
                    ) {
                        RetryableEmojiImage(
                            url = url,
                            contentDescription = emojiTextCode,
                            isPixelArt = isPixelArt,
                            modifier = Modifier.size(emojiSize)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RetryableEmojiImage(
    url: String,
    contentDescription: String?,
    isPixelArt: Boolean,
    modifier: Modifier = Modifier
) {
    var retryCount by remember { mutableIntStateOf(0) }
    var triggerRetry by remember { mutableStateOf(false) }

    LaunchedEffect(triggerRetry) {
        if (triggerRetry && retryCount < 3) {
            delay(1500.milliseconds)
            retryCount++
            triggerRetry = false
        }
    }

    if (isPixelArt) {
        val context = LocalContext.current
        var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

        LaunchedEffect(url, retryCount) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .memoryCacheKey("$url?retry=$retryCount")
                    .diskCacheKey("$url?retry=$retryCount")
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    imageBitmap = result.image.toBitmap().asImageBitmap()
                } else {
                    if (retryCount < 3 && !triggerRetry) {
                        triggerRetry = true
                    }
                }
            } catch (_: Exception) {
                if (retryCount < 3 && !triggerRetry) {
                    triggerRetry = true
                }
            }
        }

        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (imageBitmap != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(
                        image = imageBitmap!!,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.None
                    )
                }
            }
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .memoryCacheKey("$url?retry=$retryCount")
                .diskCacheKey("$url?retry=$retryCount")
                .build(),
            contentDescription = contentDescription,
            modifier = modifier,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    if (retryCount < 3 && !triggerRetry) {
                        triggerRetry = true
                    }
                }
            }
        )
    }
}


@Composable
fun CommentInputPlaceholder(
    isLoggedIn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { if (isLoggedIn) onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isLoggedIn) stringResource(R.string.comment_hint_say_something) else stringResource(
                    R.string.comment_msg_login_to_comment
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun cleanCommentForQuote(content: String): String {
    var clean =
        content.replace(Regex("""\[quote][\s\S]*?\[/quote]""", RegexOption.IGNORE_CASE), "")
    clean = clean.replace(Regex("""\[img][\s\S]*?\[/img]""", RegexOption.IGNORE_CASE), "")
    clean = clean.replace(Regex("""\[mask][\s\S]*?\[/mask]""", RegexOption.IGNORE_CASE), "")
    clean = clean.replace(Regex("""\[/?[a-zA-Z]+(?:=[^]]*)?]"""), "")
    return clean.trim()
}
