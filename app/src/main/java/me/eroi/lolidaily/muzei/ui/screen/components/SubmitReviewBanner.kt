package me.eroi.lolidaily.muzei.ui.screen.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R

private const val KEY_SUBMIT_REVIEW_DISMISSED = "submit_review_dismissed"

@Composable
fun SubmitReviewBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs =
        remember(context) {
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        }

    var dismissed by remember {
        mutableStateOf(prefs.getBoolean(KEY_SUBMIT_REVIEW_DISMISSED, false))
    }

    if (dismissed) return

    InfoBanner(
        icon = Icons.Default.MarkEmailRead,
        title = stringResource(R.string.banner_submit_review_title),
        description = stringResource(R.string.banner_submit_review_description),
        onDismiss = {
            prefs.edit { putBoolean(KEY_SUBMIT_REVIEW_DISMISSED, true) }
            dismissed = true
        },
        modifier = modifier,
    )
}
