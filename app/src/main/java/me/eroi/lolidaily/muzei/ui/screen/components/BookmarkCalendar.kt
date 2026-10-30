package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.tatarka.google.material.hct.Hct
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookmarkCalendar(
    artwork: List<ArtworkPreview>,
    selectedDate: String?,
    onDateSelected: (String?) -> Unit,
    scrollConnection: NestedScrollConnection,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val initial = selectedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    val currentMonth = YearMonth.from(today)
    val locale = LocalConfiguration.current.locales[0]
    val navigationMinWidth = 96.dp + 80.dp * LocalDensity.current.fontScale
    val marks =
        remember(artwork) {
            artwork.groupBy { it.bookmarkDate }.mapValues { (_, entries) -> entries.map { it.tags }.toSet() }
        }
    val lc0 = MaterialTheme.colorScheme.primary
    // Neutral and monochrome schemes can give primary and tertiary almost identical hues.
    // Derive a contrasting accent from the live primary, preserving its light/dark tone.
    val lces =
        remember(lc0) {
            val primary = Hct.fromInt(lc0.toArgb())
            Color(Hct.from((primary.hue + 180.0) % 360.0, maxOf(primary.chroma, 48.0), primary.tone).toInt())
        }
    var monthMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }
    val firstMonth = YearMonth.of(DatePickerDefaults.YearRange.first, 1)
    val choosingPeriod = monthMenu || yearMenu
    Column(
        modifier.fillMaxWidth().background(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        ).padding(horizontal = 8.dp),
    ) {
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2) {
            Row(
                Modifier.widthIn(min = navigationMinWidth).weight(1f).heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!choosingPeriod) {
                    IconButton(
                        onClick = { month = month.minusMonths(1) },
                        enabled = month > firstMonth,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.bookmark_previous_month))
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                Box(Modifier.weight(1f)) {
                    TextButton(
                        onClick = { monthMenu = !monthMenu },
                        enabled = !yearMenu,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    ) {
                        Text(month.month.getDisplayName(TextStyle.SHORT, locale))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.bookmark_choose_month),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                if (!choosingPeriod) {
                    IconButton(
                        onClick = { month = month.plusMonths(1) },
                        enabled = month < currentMonth,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.bookmark_next_month))
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }
            Row(
                Modifier.widthIn(min = navigationMinWidth).weight(1f).heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!choosingPeriod) {
                    IconButton(
                        onClick = { month = month.minusYears(1) },
                        enabled = month.year > firstMonth.year,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.bookmark_previous_year))
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                Box(Modifier.weight(1f)) {
                    TextButton(
                        onClick = { yearMenu = !yearMenu },
                        enabled = !monthMenu,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    ) {
                        Text(month.year.toString())
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            stringResource(R.string.bookmark_choose_year),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                if (!choosingPeriod) {
                    IconButton(
                        onClick = { month = minOf(month.plusYears(1), currentMonth) },
                        enabled = month.year < today.year,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.bookmark_next_year))
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(396.dp)) {
            if (choosingPeriod) {
                if (monthMenu) {
                    BookmarkCalendarOptions(
                        values = 1..12,
                        selectedValue = month.monthValue,
                        label = { java.time.Month.of(it).getDisplayName(TextStyle.FULL, locale) },
                        isEnabled = { YearMonth.of(month.year, it) <= currentMonth },
                        onSelected = {
                            month = month.withMonth(it)
                            monthMenu = false
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BookmarkCalendarOptions(
                        values = DatePickerDefaults.YearRange,
                        selectedValue = month.year,
                        label = { it.toString() },
                        isEnabled = { it <= today.year },
                        onSelected = { year ->
                            val number = if (year == today.year) minOf(month.monthValue, today.monthValue) else month.monthValue
                            month = YearMonth.of(year, number)
                            yearMenu = false
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().nestedScroll(scrollConnection).scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val calendarWidth = maxOf(maxWidth, 336.dp)
                        Box(Modifier.horizontalScroll(rememberScrollState())) {
                            Column(Modifier.width(calendarWidth)) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    java.time.DayOfWeek.entries.forEach { day ->
                                        Box(
                                            Modifier.weight(1f).heightIn(min = 48.dp).clearAndSetSemantics {
                                                contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                day.getDisplayName(TextStyle.NARROW, locale),
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                                val offset = month.atDay(1).dayOfWeek.value - 1
                                // Always reserve six weeks so four-, five- and six-week months share one layout.
                                repeat(6) { week ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp)) {
                                        repeat(7) { weekday ->
                                            val day = week * 7 + weekday - offset + 1
                                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                if (day in 1..month.lengthOfMonth()) {
                                                    val date = month.atDay(day)
                                                    val tags = marks[date.toString()].orEmpty()
                                                    val hasLc0OrYj = "LC0" in tags || "LC YJ" in tags
                                                    val enabled = date <= today
                                                    val isSelected = date.toString() == selectedDate
                                                    val label =
                                                        stringResource(
                                                            R.string.bookmark_calendar_day,
                                                            date.format(
                                                                DateTimeFormatter.ofLocalizedDate(
                                                                    java.time.format.FormatStyle.FULL,
                                                                ).withLocale(locale),
                                                            ),
                                                            listOfNotNull(
                                                                "LC0 / LC YJ".takeIf { hasLc0OrYj },
                                                                "LC ES".takeIf { it in tags },
                                                            ).joinToString(),
                                                        )
                                                    Column(
                                                        Modifier.fillMaxWidth(),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center,
                                                    ) {
                                                        // Reserve the 48dp Material touch target separately from the markers.
                                                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                                            Surface(
                                                                selected = isSelected,
                                                                onClick = { onDateSelected(date.toString()) },
                                                                enabled = enabled,
                                                                modifier =
                                                                    Modifier.size(
                                                                        40.dp,
                                                                    ).semantics { contentDescription = label },
                                                                shape = CircleShape,
                                                                color =
                                                                    if (isSelected) {
                                                                        MaterialTheme.colorScheme.primary
                                                                    } else {
                                                                        Color.Transparent
                                                                    },
                                                                border =
                                                                    if (date == today && !isSelected) {
                                                                        BorderStroke(
                                                                            1.dp,
                                                                            MaterialTheme.colorScheme.primary,
                                                                        )
                                                                    } else {
                                                                        null
                                                                    },
                                                            ) {
                                                                Box(
                                                                    Modifier.requiredSize(40.dp),
                                                                    contentAlignment = Alignment.Center,
                                                                ) {
                                                                    Text(
                                                                        day.toString(),
                                                                        modifier = Modifier.clearAndSetSemantics {},
                                                                        style = MaterialTheme.typography.bodyLarge,
                                                                        color =
                                                                            when {
                                                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                                                !enabled ->
                                                                                    MaterialTheme.colorScheme.onSurface.copy(
                                                                                        alpha = 0.38f,
                                                                                    )
                                                                                date == today -> MaterialTheme.colorScheme.primary
                                                                                else -> MaterialTheme.colorScheme.onSurface
                                                                            },
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Row(
                                                            Modifier.height(10.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            if (hasLc0OrYj) BookmarkDot(lc0)
                                                            if ("LC ES" in tags) BookmarkDot(lces)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                Modifier.heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BookmarkDot(lc0)
                    Text("LC0 / LC YJ", style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BookmarkDot(lces)
                    Text("LC ES", style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = { onDateSelected(null) }, enabled = selectedDate != null) {
                Text(stringResource(R.string.bookmark_clear_date_filter))
            }
        }
    }
}

@Composable
private fun BookmarkCalendarOptions(
    values: IntRange,
    selectedValue: Int,
    label: (Int) -> String,
    isEnabled: (Int) -> Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selectedValue - values.first - 3).coerceAtLeast(0))
    val scrollbar = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp).selectableGroup(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(values.toList(), key = { it }) { value ->
                val isSelected = value == selectedValue
                val enabled = isEnabled(value)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                        .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelected(value) })
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        label(value),
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                    )
                }
            }
        }
        Canvas(Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp).width(4.dp).fillMaxHeight()) {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isNotEmpty()) {
                val rowHeight = visible.first().size.toFloat()
                val contentHeight = rowHeight * info.totalItemsCount
                val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                if (contentHeight > viewport) {
                    val height = (size.height * viewport / contentHeight).coerceAtLeast(24.dp.toPx()).coerceAtMost(size.height)
                    val offset = state.firstVisibleItemIndex * rowHeight + state.firstVisibleItemScrollOffset
                    val progress = (offset / (contentHeight - viewport)).coerceIn(0f, 1f)
                    drawRoundRect(
                        scrollbar,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - height) * progress),
                        size = androidx.compose.ui.geometry.Size(size.width, height),
                        cornerRadius = CornerRadius(size.width / 2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkDot(color: Color) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
}
