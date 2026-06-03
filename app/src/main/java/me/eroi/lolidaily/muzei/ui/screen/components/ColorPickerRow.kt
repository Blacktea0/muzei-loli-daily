package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.eroi.lolidaily.muzei.R
import kotlin.math.roundToInt

@Composable
fun ManualColorPickerRow(
    currentColorArgb: Int,
    onColorChanged: (Int) -> Unit,
) {
    var hexInput by remember(currentColorArgb) {
        mutableStateOf("#%06X".format(currentColorArgb and 0xFFFFFF))
    }
    var isError by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

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
                modifier =
                    Modifier
                        .size(40.dp)
                        .clickable { showColorPicker = true },
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

    if (showColorPicker) {
        ColorPickerDialog(
            currentColorArgb = currentColorArgb,
            onColorSelected = { color ->
                showColorPicker = false
                onColorChanged(color)
                hexInput = "#%06X".format(color and 0xFFFFFF)
                isError = false
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

@Composable
private fun ColorPickerDialog(
    currentColorArgb: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Convert initial color to HSV
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(currentColorArgb or 0xFF000000.toInt(), hsv)
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }

    val selectedColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_color_picker)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Color preview
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(selectedColor),
                )

                // Hue slider
                Column {
                    Text(
                        text = "Hue: ${hue.roundToInt()}°",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors =
                                            (0..360 step 30).map { h ->
                                                Color(android.graphics.Color.HSVToColor(floatArrayOf(h.toFloat(), 1f, 1f)))
                                            },
                                    ),
                                ),
                    )
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Saturation slider
                Column {
                    Text(
                        text = "Saturation: ${(saturation * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, brightness))),
                                                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, brightness))),
                                            ),
                                    ),
                                ),
                    )
                    Slider(
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Value/Brightness slider
                Column {
                    Text(
                        text = "Brightness: ${(brightness * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 0f))),
                                                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))),
                                            ),
                                    ),
                                ),
                    )
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selectedColor.toArgb()) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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
