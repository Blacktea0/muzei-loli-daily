package me.eroi.lolidaily.muzei.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.ui.screen.components.SegmentedSettingsGroup

private data class ThirdPartyService(
    val name: String,
    val description: String,
    val url: String,
)

private data class ThirdPartyLibrary(
    val name: String,
    val description: String,
    val license: String,
)

private val services = listOf(
    ThirdPartyService(
        name = "Loli Commons API",
        description = "Daily artwork collection API",
        url = "https://lolicommons.tsuki.ga",
    ),
    ThirdPartyService(
        name = "Bangumi API",
        description = "Anime/manga database and community",
        url = "https://bgm.tv",
    ),
    ThirdPartyService(
        name = "chii.ai",
        description = "Character search and autocomplete",
        url = "https://chii.ai",
    ),
    ThirdPartyService(
        name = "VxTwitter API",
        description = "Twitter/X media proxy",
        url = "https://github.com/dylanpdx/BetterTwitFix",
    ),
)

private val libraries = listOf(
    ThirdPartyLibrary(
        name = "Jetpack Compose",
        description = "Modern toolkit for building native Android UI",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "Material Design 3",
        description = "Material Design 3 components for Jetpack Compose",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "Coil",
        description = "Image loading library for Android",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "OkHttp",
        description = "HTTP client for Android and Java",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "kotlinx.serialization",
        description = "Kotlin multiplatform serialization",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "kotlinx.coroutines",
        description = "Kotlin coroutines library",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "Room",
        description = "SQLite abstraction for local data persistence",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "WorkManager",
        description = "Background task scheduling",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "Muzei API",
        description = "Wallpaper source plugin framework",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "material-color-utilities",
        description = "Material You dynamic color scheme generation",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "Zoomable",
        description = "Pinch-to-zoom with snap-back for Compose",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "Wavy Slider",
        description = "Wave-style slider component",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "Palette KTX",
        description = "Extract dominant colors from bitmaps",
        license = "Apache-2.0",
    ),
    ThirdPartyLibrary(
        name = "AppCompat",
        description = "Backward-compatible Android framework support",
        license = "Apache-2.0",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcknowledgmentsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_acknowledgments)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Third-party API Services ──
            item {
                SectionHeader(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.section_third_party_services),
                )
            }

            item {
                SegmentedSettingsGroup {
                    services.forEach { service ->
                        ServiceRow(service)
                    }
                }
            }

            // ── Third-party Libraries ──
            item {
                SectionHeader(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    title = stringResource(R.string.section_third_party_libraries),
                )
            }

            item {
                SegmentedSettingsGroup {
                    libraries.forEach { library ->
                        LibraryRow(library)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ServiceRow(service: ThirdPartyService) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = service.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = service.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = service.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryRow(library: ThirdPartyLibrary) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = library.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = "${stringResource(R.string.label_license)}: ${library.license}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
