package me.eroi.lolidaily.muzei.ui.screen.pages

import android.content.Intent
import androidx.core.net.toUri
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import coil3.compose.AsyncImage
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.ui.screen.components.DomainPickerDialog

@Composable
fun AccountSheet(
    isLoggedIn: Boolean,
    bgmDomain: String,
    bgmUsername: String?,
    bgmNickname: String?,
    bgmAvatarUrl: String?,
    lcBadge: String?,
    onBadgeChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDomainChanged: (String) -> Unit,
) {
    var showDomainPicker by remember { mutableStateOf(false) }
    var showBadgePicker by remember { mutableStateOf(false) }

    SheetTitle(Icons.Filled.AccountCircle, stringResource(R.string.title_bangumi_account))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AccountAvatar(
                    avatarUrl = if (isLoggedIn) bgmAvatarUrl else null,
                    nickname = bgmNickname ?: bgmUsername,
                    size = 52.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (isLoggedIn) {
                                bgmNickname ?: bgmUsername ?: stringResource(R.string.status_logged_in)
                            } else {
                                stringResource(R.string.status_not_logged_in)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (isLoggedIn && bgmUsername != null) {
                                "@$bgmUsername · $bgmDomain"
                            } else {
                                stringResource(R.string.label_via_domain, bgmDomain)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isLoggedIn && !lcBadge.isNullOrBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                val context = LocalContext.current
                LcBadgeImage(
                    badge = lcBadge,
                    onClick = {
                        if (bgmUsername != null) {
                            val url = "https://$bgmDomain/user/$bgmUsername"
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                    },
                )
            }
        }
    }

    if (isLoggedIn) {
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_logout))
        }
    } else {
        FilledTonalButton(onClick = { showDomainPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_login))
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

    if (showBadgePicker) {
        BadgePickerDialog(
            currentBadge = lcBadge ?: "LC0",
            onBadgeSelected = { newBadge ->
                showBadgePicker = false
                onBadgeChanged(newBadge)
            },
            onDismiss = { showBadgePicker = false },
        )
    }
}

@Composable
fun AccountAvatar(
    avatarUrl: String?,
    nickname: String?,
    size: Dp,
) {
    val context = LocalContext.current
    val appIcon = remember {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(size),
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)),
            )
        } else {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

@Composable
private fun LcBadgeImage(
    badge: String,
    onClick: (() -> Unit)? = null,
) {
    val svgUri = "file:///android_asset/lc_badges/$badge.svg"
    AsyncImage(
        model = svgUri,
        contentDescription = badge,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

private fun computeBadge(
    q1: Int,
    q2: Int,
    pg: Boolean,
): String {
    if (q1 == 0 && q2 == 1) return "LC0"
    val parts = mutableListOf<String>()
    if (q2 == 0) parts += "YJ"
    if (q1 >= 1) {
        parts += "ES"
        if (q1 >= 2) parts += "NC"
        if (pg) parts += "PG"
        if (q1 >= 3) parts += "GR"
    }
    return "LC ${parts.joinToString("-")}"
}

private fun parseBadgeQuestions(badge: String): Triple<Int, Int, Boolean> {
    val hasYJ = badge.contains("YJ")
    val q2 = if (hasYJ) 0 else 1
    val hasPG = badge.contains("PG")
    val q1 =
        when {
            badge.contains("GR") -> 3
            badge.contains("NC") -> 2
            badge.contains("ES") -> 1
            else -> 0
        }
    return Triple(q1, q2, hasPG)
}

@Composable
private fun BadgePickerDialog(
    currentBadge: String,
    onBadgeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val (initQ1, initQ2, initPg) = remember { parseBadgeQuestions(currentBadge) }
    var q1 by remember { mutableIntStateOf(initQ1) }
    var q2 by remember { mutableIntStateOf(initQ2) }
    var pg by remember { mutableStateOf(initPg) }
    val resultBadge = remember(q1, q2, pg) { computeBadge(q1, q2, pg) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.title_badge_picker),
                    style = MaterialTheme.typography.titleMedium,
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.badge_q1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        R.string.badge_q1_a0,
                        R.string.badge_q1_a1,
                        R.string.badge_q1_a2,
                        R.string.badge_q1_a3,
                    ).forEachIndexed { index, resId ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        q1 = index
                                        if (index == 0) pg = false
                                    }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = q1 == index, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(resId),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.badge_q2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        R.string.badge_q2_a0,
                        R.string.badge_q2_a1,
                    ).forEachIndexed { index, resId ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { q2 = index }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = q2 == index, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(resId),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                if (q1 >= 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.badge_q3),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pg = true }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = pg, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.badge_q3_a0),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pg = false }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = !pg, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.badge_q3_a1),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                AsyncImage(
                    model = "file:///android_asset/lc_badges/$resultBadge.svg",
                    contentDescription = resultBadge,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    FilledTonalButton(onClick = { onBadgeSelected(resultBadge) }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsChoiceGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(4.dp), content = content)
    }
}

@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
fun ChoiceRowWithBadge(
    badgeAsset: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        AsyncImage(
            model = "file:///android_asset/$badgeAsset",
            contentDescription = null,
            modifier = Modifier.height(36.dp).weight(1f),
        )
    }
}
