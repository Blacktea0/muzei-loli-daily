package me.eroi.lolidaily.muzei.util

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import java.io.File

/**
 * Copies the artwork file to the public Pictures/LoliDaily directory via [MediaStore], compatible
 * with API 24+.
 */
fun exportArtwork(
    context: android.content.Context,
    preview: ArtworkPreview,
) {
    try {
        val resolver = context.contentResolver
        val mimeType =
            if (preview.filename.endsWith(".png", true)) {
                "image/png"
            } else if (preview.filename.endsWith(".gif", true)) {
                "image/gif"
            } else if (preview.filename.endsWith(".webp", true)) {
                "image/webp"
            } else {
                "image/jpeg"
            }

        val relativePath = Environment.DIRECTORY_PICTURES + "/LoliDaily"

        val destDir =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "LoliDaily",
                )
            } else {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "LoliDaily",
                )
            }
        val destFile = File(destDir, preview.filename)
        if (destFile.exists()) {
            Toast.makeText(context, context.getString(R.string.msg_already_exported), Toast.LENGTH_SHORT).show()
            return
        }

        val contentValues =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, preview.filename)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

        val outputUri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: run {
                    Toast.makeText(context, context.getString(R.string.msg_export_file_failed), Toast.LENGTH_SHORT)
                        .show()
                    return
                }

        resolver.openInputStream(preview.uri)?.use { input ->
            resolver.openOutputStream(outputUri)?.use { output -> input.copyTo(output) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(outputUri, contentValues, null, null)
        }

        Toast.makeText(context, context.getString(R.string.msg_saved_to_pictures), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.msg_export_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
    }
}
