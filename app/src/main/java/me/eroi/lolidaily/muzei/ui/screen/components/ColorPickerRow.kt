package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.eroi.lolidaily.muzei.R

@Composable
fun ManualColorPickerRow(
    currentColorArgb: Int,
    onColorChanged: (Int) -> Unit,
) {
    var hexInput by remember(currentColorArgb) {
        mutableStateOf("#%06X".format(currentColorArgb and 0xFFFFFF))
    }
    var isError by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color(currentColorArgb),
                modifier = Modifier.size(40.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {}

            OutlinedTextField(
                value = hexInput,
                onValueChange = { value ->
                    hexInput = value
                    val parsed = parseHexColor(value)
                    if (parsed != null) {
                        isError = false
                        onColorChanged(parsed)
                    } else {
                        isError = true
                    }
                },
                label = { Text(stringResource(R.string.label_hex_color)) },
                singleLine = true,
                isError = isError,
                supportingText =
                    if (isError) {
                        { Text(stringResource(R.string.msg_invalid_hex_color)) }
                    } else {
                        null
                    },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun parseHexColor(input: String): Int? {
    val hex = input.removePrefix("#").trim()
    if (hex.length != 6) return null
    return try {
        0xFF000000.toInt() or hex.toLong(16).toInt()
    } catch (_: NumberFormatException) {
        null
    }
}
