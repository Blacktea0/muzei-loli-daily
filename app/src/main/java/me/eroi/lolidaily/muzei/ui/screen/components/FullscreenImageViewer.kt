package me.eroi.lolidaily.muzei.ui.screen.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.eroi.lolidaily.muzei.R
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

/**
 * Full-screen zoomable image viewer dialog.
 *
 * @param model Image data — can be [android.net.Uri], [ByteArray], URL string, or Coil [ImageRequest].
 * @param filename Content description for the image.
 * @param onDismiss Called when the user dismisses the viewer.
 */
@Composable
fun FullscreenImageViewer(
    model: Any,
    filename: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zoomState = rememberZoomState()

    var showAppBar by remember { mutableStateOf(false) }
    val currentShowAppBar by rememberUpdatedState(showAppBar)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val dialogView = LocalView.current
        DisposableEffect(Unit) {
            val parentView = dialogView.parent as? android.view.View
            val dialogWindow = (parentView as? androidx.compose.ui.window.DialogWindowProvider)?.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = dialogWindow?.insetsController
                controller?.hide(android.view.WindowInsets.Type.statusBars())
                controller?.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                onDispose {
                    controller?.show(android.view.WindowInsets.Type.statusBars())
                }
            } else {
                @Suppress("DEPRECATION")
                dialogWindow?.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                )
                onDispose {
                    @Suppress("DEPRECATION")
                    dialogWindow?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(model).build(),
                contentDescription = filename,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier.fillMaxSize()
                        .zoomable(
                            zoomState,
                            enableOneFingerZoom = false,
                            onTap = {
                                if (currentShowAppBar) showAppBar = false else showAppBar = true
                            },
                        ),
            )

            AnimatedVisibility(
                visible = showAppBar,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .statusBarsPadding()
                            .padding(4.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_close),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
