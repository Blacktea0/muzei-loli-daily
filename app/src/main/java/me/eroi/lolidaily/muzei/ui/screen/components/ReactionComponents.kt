package me.eroi.lolidaily.muzei.ui.screen.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.worker.EmojiMap
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.key
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.model.BangumiReaction
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope

@Composable
fun rememberPixelBitmap(resId: Int): ImageBitmap {
    val resources = LocalResources.current
    return remember(resId) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(resources, resId, opts)
        bitmap.asImageBitmap()
    }
}

@Composable
fun PixelEmoji(
    resId: Int,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = rememberPixelBitmap(resId)
    Canvas(modifier = modifier) {
        drawImage(
            image = imageBitmap,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

@Composable
fun ReactionPickerDialog(
    onDismiss: () -> Unit,
    onEmojiSelected: (Int) -> Unit,
    emojis: List<Int> = listOf(0, 104, 54, 140, 122, 90, 88, 80),
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.reaction_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )


                for (row in emojis.chunked(4)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        row.forEach { value ->
                            val resId = EmojiMap.emojiResId(value) ?: return@forEach
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier =
                                    Modifier
                                        .clickable { onEmojiSelected(value) }
                                        .padding(8.dp),
                            ) {
                                PixelEmoji(resId = resId, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.label_tap_emoji_react),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionChip(
    reactionCount: Int,
    resId: Int,
    selected: Boolean,
    tooltipText: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedTriggerKey: Any? = null,
    height: Dp = 22.dp,
    emojiSize: Dp = 16.dp,
    horizontalPadding: Dp = 6.dp,
    spacing: Dp = 2.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)

    var optimisticSelected by remember { mutableStateOf(selected) }
    var optimisticCount by remember { mutableIntStateOf(reactionCount) }

    LaunchedEffect(selected, reactionCount) {
        optimisticSelected = selected
        optimisticCount = reactionCount
    }

    val animationState = rememberReactionAnimationState(
        selected = optimisticSelected,
        containerColor = containerColor,
        selectedContainerColor = selectedContainerColor,
        contentColor = contentColor,
        selectedContentColor = selectedContentColor,
        selectedTriggerKey = selectedTriggerKey
    )

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        tooltip = {
            PlainTooltip {
                Text(
                    text = tooltipText,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }
        },
        state = tooltipState,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
        ) {
            if (animationState.animProgress < 1.0f) {
                ExplosionEffect(
                    progress = animationState.animProgress,
                    modifier = Modifier.matchParentSize()
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = animationState.containerColor,
                contentColor = animationState.contentColor,
                modifier = Modifier
                    .height(height)
                    .scale(animationState.scale * animationState.buttonScale)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (enabled) {
                                    if (optimisticSelected) {
                                        optimisticSelected = false
                                        optimisticCount = (optimisticCount - 1).coerceAtLeast(0)
                                    } else {
                                        optimisticSelected = true
                                        optimisticCount += 1
                                    }
                                }
                                onTap()
                            },
                            onLongPress = {
                                onLongPress()
                                scope.launch { tooltipState.show() }
                            }
                        )
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    PixelEmoji(
                        resId = resId,
                        modifier = Modifier
                            .size(emojiSize)
                            .graphicsLayer {
                                scaleX = animationState.emojiScale
                                scaleY = animationState.emojiScale
                            }
                    )
                    Text(
                        text = "$optimisticCount",
                        style = textStyle,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReactionChips(
    reactions: List<BangumiReaction>,
    modifier: Modifier = Modifier,
    onReactionClick: ((Int) -> Unit)? = null,
    onAddReaction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val username = SessionManager.loadUsername(context)
    val currentSelectedValues = remember(reactions, username) {
        if (username == null) {
            emptySet()
        } else {
            reactions
                .filter { r -> r.users.any { it.username == username } }
                .map { it.value }
                .toSet()
        }
    }
    var previousSelectedValues by remember { mutableStateOf(currentSelectedValues) }
    var triggeredSelectedValue by remember { mutableStateOf<Int?>(null) }
    var selectedTriggerId by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentSelectedValues) {
        val newlySelectedValue =
            findNewlySelectedReactionValue(currentSelectedValues, previousSelectedValues)
        if (newlySelectedValue != null) {
            triggeredSelectedValue = newlySelectedValue
            selectedTriggerId++
        }
        previousSelectedValues = currentSelectedValues
    }

    val valid = remember(reactions) {
        reactions.filter { it.users.isNotEmpty() }
            .mapNotNull { r -> EmojiMap.emojiResId(r.value)?.let { r to it } }
    }
    val isLoggedIn = username != null

    if (valid.isEmpty() && (onAddReaction == null || !isLoggedIn)) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((reaction, resId) in valid) {
            val selected = username != null && reaction.users.any { it.username == username }
            val animTriggerKey =
                if (reaction.value == triggeredSelectedValue) selectedTriggerId else null
            key(reaction.value) {
                ReactionChip(
                    reactionCount = reaction.users.size,
                    resId = resId,
                    selected = selected,
                    enabled = onReactionClick != null,
                    selectedTriggerKey = animTriggerKey,
                    tooltipText = formatUserList(reaction.users.map {
                        it.nickname.takeIf { n -> n.isNotBlank() } ?: it.username
                    }),
                    onTap = {
                        onReactionClick?.invoke(reaction.value)
                    },
                    onLongPress = {}
                )
            }
        }

        // Add reaction button
        if (isLoggedIn && onAddReaction != null) {
            IconButton(
                onClick = { onAddReaction() },
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(50)
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.content_desc_add_reaction),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun findNewlySelectedReactionValue(
    currentSelectedValues: Set<Int>,
    previousSelectedValues: Set<Int>,
): Int? = (currentSelectedValues - previousSelectedValues).firstOrNull()

private fun formatUserList(users: List<String>): String {
    if (users.isEmpty()) return ""
    val locale = java.util.Locale.getDefault()
    val separator = if (locale.language == "zh" || locale.language == "ja") "、" else ", "
    return users.joinToString(separator)
}


// ── Tietie Reaction Animation Helpers ──────────────────────────────

internal val ButtonEasing = CubicBezierEasing(0.17f, 1.2f, 0.32f, 1.2f)
internal val EmojiEasing = CubicBezierEasing(0.17f, 0.89f, 0.32f, 2.5f)
internal val DecayEasing = CubicBezierEasing(0.21f, 0.61f, 0.35f, 1.0f)

internal data class ComposeParticle(
    val angleRad: Double,
    val r25: Float,
    val r100: Float,
    val color: Color
)

internal val HeptagonAParticles = (0..6).map { i ->
    ComposeParticle(
        angleRad = Math.toRadians(277.59 + i * (360.0 / 7.0)),
        r25 = 39.3f,
        r100 = 48.3f,
        color = listOf(
            Color(0xFFFF8080), Color(0xFFFFED80), Color(0xFFA4FF80),
            Color(0xFF80FFC8), Color(0xFF80C8FF), Color(0xFFA480FF), Color(0xFFFF80ED)
        )[i]
    )
}

internal val HeptagonBParticles = (0..6).map { i ->
    ComposeParticle(
        angleRad = Math.toRadians(261.05 + i * (360.0 / 7.0)),
        r25 = 33.4f,
        r100 = 42.3f,
        color = listOf(
            Color(0xFFFFED80), Color(0xFFA4FF80), Color(0xFF80FFC8),
            Color(0xFF80C8FF), Color(0xFFA480FF), Color(0xFFFF80ED), Color(0xFFFF8080)
        )[i]
    )
}

@Stable
internal class ReactionAnimationState(
    private val animProgressAnimatable: Animatable<Float, AnimationVector1D>,
    private val buttonScaleAnimatable: Animatable<Float, AnimationVector1D>,
    private val emojiScaleAnimatable: Animatable<Float, AnimationVector1D>,
    private val scaleState: State<Float>,
    private val containerColorState: State<Color>,
    private val contentColorState: State<Color>
) {
    val animProgress: Float get() = animProgressAnimatable.value
    val buttonScale: Float get() = buttonScaleAnimatable.value
    val emojiScale: Float get() = emojiScaleAnimatable.value

    val scale: Float get() = scaleState.value
    val containerColor: Color get() = containerColorState.value
    val contentColor: Color get() = contentColorState.value

    suspend fun playExplosion() {
        coroutineScope {
            launch {
                animProgressAnimatable.snapTo(0f)
                animProgressAnimatable.animateTo(1f, tween(2000, easing = LinearEasing))
            }
            launch {
                buttonScaleAnimatable.snapTo(0f)
                buttonScaleAnimatable.animateTo(1f, tween(500, easing = { fraction ->
                    if (fraction < 0.175f) 0f
                    else ButtonEasing.transform((fraction - 0.175f) / 0.825f)
                }))
            }
            launch {
                emojiScaleAnimatable.snapTo(0f)
                emojiScaleAnimatable.animateTo(1f, tween(1000, easing = { fraction ->
                    if (fraction < 0.175f) 0f
                    else EmojiEasing.transform((fraction - 0.175f) / 0.825f)
                }))
            }
        }
    }

    suspend fun cancel() {
        animProgressAnimatable.snapTo(1f)
        buttonScaleAnimatable.snapTo(1f)
        emojiScaleAnimatable.snapTo(1f)
    }
}

@Composable
internal fun rememberReactionAnimationState(
    selected: Boolean,
    containerColor: Color,
    selectedContainerColor: Color,
    contentColor: Color,
    selectedContentColor: Color,
    selectedTriggerKey: Any? = null
): ReactionAnimationState {
    val animProgress = remember { Animatable(1f) }
    val buttonScale = remember { Animatable(1f) }
    val emojiScale = remember { Animatable(1f) }

    val currentContainerColor = if (selected) selectedContainerColor else containerColor
    val currentContentColor = if (selected) selectedContentColor else contentColor

    val animatedContainerColor = animateColorAsState(
        targetValue = currentContainerColor,
        animationSpec = if (selected) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            snap()
        },
        label = "reactionContainerColor"
    )

    val animatedContentColor = animateColorAsState(
        targetValue = currentContentColor,
        animationSpec = if (selected) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            snap()
        },
        label = "reactionContentColor"
    )

    val scale = animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = if (selected) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            snap()
        },
        label = "reactionScale"
    )

    val state = remember {
        ReactionAnimationState(
            animProgressAnimatable = animProgress,
            buttonScaleAnimatable = buttonScale,
            emojiScaleAnimatable = emojiScale,
            scaleState = scale,
            containerColorState = animatedContainerColor,
            contentColorState = animatedContentColor
        )
    }

    var prevSelected by remember { mutableStateOf(selected) }
    var skipNextSelectedTrigger by remember { mutableStateOf(false) }
    var explosionTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(selected, selectedTriggerKey) {
        if (!selected) {
            state.cancel()
            skipNextSelectedTrigger = false
        } else if (!prevSelected) {
            explosionTrigger++
            skipNextSelectedTrigger = true
        } else if (selectedTriggerKey != null) {
            if (skipNextSelectedTrigger) {
                skipNextSelectedTrigger = false
            } else {
                explosionTrigger++
            }
        }
        prevSelected = selected
    }

    LaunchedEffect(explosionTrigger) {
        if (explosionTrigger > 0) {
            state.playExplosion()
        }
    }

    return state
}

@Composable
internal fun ExplosionEffect(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val maxBubbleRadius = with(density) { 24.dp.toPx() }
    val maxParticleRadius = with(density) { 3.dp.toPx() }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)

        // 1. Bubble circle expanding and hollowing out
        if (progress < 0.30f) {
            val scale: Float
            val strokeWidth: Float
            if (progress < 0.15f) {
                val k = progress / 0.15f
                scale = DecayEasing.transform(k)
                strokeWidth = maxBubbleRadius
            } else {
                val k = (progress - 0.15f) / 0.15f
                scale = 1f
                strokeWidth = maxBubbleRadius * (1f - DecayEasing.transform(k))
            }
            if (strokeWidth > 0f) {
                drawCircle(
                    color = Color(0xFFF09199),
                    radius = scale * maxBubbleRadius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        // 2. 14 rainbow particles burst
        val tp = progress / 0.75f
        if (tp in 0.20f..1.0f) {
            val p: Float
            val opacity: Float
            val particleRadius: Float

            if (tp < 0.25f) {
                val k = (tp - 0.20f) / 0.05f
                p = 0f
                opacity = DecayEasing.transform(k)
                particleRadius = maxParticleRadius
            } else {
                val k = (tp - 0.25f) / 0.75f
                p = DecayEasing.transform(k)
                opacity = 1f - p
                particleRadius = maxParticleRadius * (1f - p)
            }

            if (particleRadius > 0f && opacity > 0f) {
                HeptagonAParticles.forEach { particle ->
                    val currentRadius = with(density) {
                        (particle.r25 + (particle.r100 - particle.r25) * p).dp.toPx()
                    }
                    val x = (currentRadius * cos(particle.angleRad)).toFloat()
                    val y = (currentRadius * sin(particle.angleRad)).toFloat()
                    drawCircle(
                        color = particle.color,
                        radius = particleRadius,
                        center = center + Offset(x, y),
                        alpha = opacity
                    )
                }

                HeptagonBParticles.forEach { particle ->
                    val currentRadius = with(density) {
                        (particle.r25 + (particle.r100 - particle.r25) * p).dp.toPx()
                    }
                    val x = (currentRadius * cos(particle.angleRad)).toFloat()
                    val y = (currentRadius * sin(particle.angleRad)).toFloat()
                    drawCircle(
                        color = particle.color,
                        radius = particleRadius,
                        center = center + Offset(x, y),
                        alpha = opacity
                    )
                }
            }
        }
    }
}
