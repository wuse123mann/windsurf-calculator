@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.wuse.windsurfrechner

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

sealed interface ForecastState {
    data object Loading : ForecastState
    data class Ok(val hours: List<Hour>, val loadedAt: LocalDateTime) : ForecastState
    data class Error(val message: String) : ForecastState
    data object OutOfArea : ForecastState
}

// ---------- Ortssuche (Android-Geocoder, ohne fremden Webdienst) ----------

data class GeoResult(val name: String, val region: String, val lat: Double, val lon: Double)

object Places {
    private val COORDS = Regex("""^\s*(-?\d{1,2}(?:[.,]\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:[.,]\d+)?)\s*$""")

    /** Erlaubt die Eingabe von Koordinaten, z. B. „47.85, 16.83“ oder „47,85 16,83“. */
    fun parseCoordinates(q: String): GeoResult? {
        val m = COORDS.matchEntire(q) ?: return null
        val lat = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return GeoResult(
            "Spot ${String.format(Locale.GERMANY, "%.3f, %.3f", lat, lon)}",
            "eigene Koordinaten",
            lat,
            lon,
        )
    }

    suspend fun search(context: Context, query: String): List<GeoResult> {
        parseCoordinates(query)?.let { return listOf(it) }
        if (!Geocoder.isPresent()) {
            throw IOException("Ortssuche auf diesem Gerät nicht verfügbar – gib Koordinaten ein, z. B. 47.85, 16.83")
        }
        val geocoder = Geocoder(context, Locale.GERMAN)
        val addresses: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(
                    query,
                    10,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resumeWithException(IOException(errorMessage ?: "Ortssuche fehlgeschlagen"))
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 10) ?: emptyList()
            }
        }
        return addresses
            .filter { it.hasLatitude() && it.hasLongitude() }
            .map { a ->
                val name = listOf(a.featureName, a.locality, a.subAdminArea)
                    .firstOrNull { !it.isNullOrBlank() && !it.all(Char::isDigit) } ?: query.trim()
                val region = listOf(a.locality, a.adminArea, a.countryName)
                    .filter { !it.isNullOrBlank() && it != name }
                    .distinct()
                    .joinToString(", ")
                GeoResult(name, region, a.latitude, a.longitude)
            }
            .distinctBy { "${it.name}|${it.region}" }
    }
}

// ---------- Tab „Spots“ ----------

@Composable
fun SpotsContent(
    spots: List<Spot>,
    settings: SpotSettings,
    quiver: Quiver,
    forecastFor: (Spot) -> ForecastState?,
    onLoad: (Boolean) -> Unit,
    onOpen: (Spot) -> Unit,
    onAdd: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onSettingsChange: (SpotSettings) -> Unit,
) {
    LaunchedEffect(spots.map { it.id }) { onLoad(false) }

    if (spots.isEmpty()) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🏄", fontSize = 48.sp)
                Text("Noch keine Spots", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Füge deine Spots hinzu. Dann siehst du für die nächsten zweieinhalb Tage Stunde für " +
                        "Stunde Wind, Böen und welches Segel und Board passt – und bekommst auf Wunsch " +
                        "einen Wind-Alarm.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${settings.weightKg.de(0)} kg · ${settings.level.label} · ${settings.sailType.label}" +
                    " · gleitbar ab ${settings.minWindKn.de(0)} kn",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onLoad(true) }) { Icon(Icons.Default.Refresh, "Aktualisieren") }
        }
        spots.forEachIndexed { index, spot ->
            SpotCard(
                spot = spot,
                settings = settings,
                quiver = quiver,
                state = forecastFor(spot),
                canUp = index > 0,
                canDown = index < spots.lastIndex,
                onUp = { onMove(index, index - 1) },
                onDown = { onMove(index, index + 1) },
                onClick = { onOpen(spot) },
            )
        }
    }

    FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.width(6.dp))
        Text("Spot hinzufügen")
    }

    AlarmSettingsCard(spots, settings, onSettingsChange)

    Text(
        GeoSphere.ATTRIBUTION + " · Vorhersage für ca. 2½ Tage im Alpenraum " +
            "(Österreich, Schweiz, Süddeutschland, Norditalien, Slowenien, Kroatien, Ungarn).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun SpotCard(
    spot: Spot,
    settings: SpotSettings,
    quiver: Quiver,
    state: ForecastState?,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(spot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (spot.alarm) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Notifications,
                                "Wind-Alarm aktiv",
                                Modifier.size(16.dp),
                                tint = if (settings.alarmEnabled) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    if (spot.region.isNotBlank()) {
                        Text(
                            spot.region,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "Nach oben")
                }
                IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Nach unten")
                }
            }
            Spacer(Modifier.height(10.dp))
            when (state) {
                null, ForecastState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Lade Vorhersage …", style = MaterialTheme.typography.bodySmall)
                }

                ForecastState.OutOfArea -> Text(
                    "Liegt außerhalb des Vorhersagegebiets (Alpenraum) – keine Vorhersage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                is ForecastState.Error -> Text(
                    "⚠ ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                is ForecastState.Ok -> {
                    val days = remember(state, settings, quiver) {
                        Advisor.days(state.hours, settings, quiver, from = LocalDateTime.now())
                    }
                    DayStrip(days)
                    Spacer(Modifier.height(8.dp))
                    val next = days.firstOrNull { it.goodHours.size >= 2 }
                    Text(
                        if (next == null) "Kein Gleitwind in Sicht" else "Gleiten: ${WindAlarm.text(next)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (next != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (next != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Übersicht pro Tag: stärkster Wind tagsüber, hervorgehoben wenn gleitbar. */
@Composable
private fun DayStrip(days: List<DaySummary>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.take(3).forEach { day ->
            val good = day.goodHours.size >= 2
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (good) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        dayLabel(day.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (good) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    WindBadge(day.maxWindKn, fontSize = 12.sp)
                    Text(
                        if (good) "${day.goodHours.size} h gleitbar" else "–",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (good) MaterialTheme.colorScheme.onSecondaryContainer else Color.Gray,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmSettingsCard(spots: List<Spot>, settings: SpotSettings, onChange: (SpotSettings) -> Unit) {
    val context = LocalContext.current
    val askPermission = rememberNotificationPermission()
    val alarmSpots = spots.filter { it.alarm }.map { it.name }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Gleitwind & Wind-Alarm 🔔", style = MaterialTheme.typography.titleMedium)
            Text("Gleitbar ab: ${settings.minWindKn.de(0)} kn", fontWeight = FontWeight.SemiBold)
            Slider(
                value = settings.minWindKn.toFloat(),
                onValueChange = { onChange(settings.copy(minWindKn = it.roundToInt().toDouble())) },
                valueRange = 6f..25f,
                steps = 18,
            )
            SmallHint("Gerechnet wird mit Mittelwind plus einem Fünftel des Böenüberschusses.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nur Stunden bei Tageslicht", Modifier.weight(1f))
                Switch(checked = settings.onlyDaylight, onCheckedChange = { onChange(settings.copy(onlyDaylight = it)) })
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wind-Alarm aktiv", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = settings.alarmEnabled,
                    onCheckedChange = {
                        if (it) askPermission()
                        onChange(settings.copy(alarmEnabled = it))
                    },
                )
            }
            SmallHint(
                "Die App prüft etwa alle 3 Stunden im Hintergrund und meldet sich einmal pro Spot und Tag, " +
                    "wenn dort genug Gleitwind angesagt ist.",
            )
            Text("Vorausschauen", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "Nur heute", 2 to "Heute + morgen", 3 to "Bis übermorgen").forEach { (d, label) ->
                    FilterChip(
                        selected = settings.alarmDays == d,
                        onClick = { onChange(settings.copy(alarmDays = d)) },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "Mindestens ${settings.alarmMinHours} gleitbare Stunde${if (settings.alarmMinHours == 1) "" else "n"}",
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = settings.alarmMinHours.toFloat(),
                onValueChange = { onChange(settings.copy(alarmMinHours = it.roundToInt())) },
                valueRange = 1f..8f,
                steps = 6,
            )
            Text(
                if (alarmSpots.isEmpty()) "Noch kein Spot mit Alarm – öffne einen Spot und tippe auf die Glocke."
                else "Spots mit Alarm: ${alarmSpots.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!WindAlarm.hasPermission(context)) {
                Text(
                    "⚠ Benachrichtigungen sind für die App nicht erlaubt.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = askPermission) { Text("Benachrichtigungen erlauben") }
            }
            OutlinedButton(
                enabled = alarmSpots.isNotEmpty(),
                onClick = {
                    askPermission()
                    WindAlarm.checkNow(context, force = true)
                    Toast.makeText(
                        context,
                        "Prüfe jetzt – bei Gleitwind kommt gleich eine Benachrichtigung",
                        Toast.LENGTH_LONG,
                    ).show()
                },
            ) { Text("Jetzt prüfen (Test)") }
        }
    }
}

@Composable
private fun SmallHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------- Spot-Detail: Stunde für Stunde ----------

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SpotDetailScreen(
    spot: Spot,
    settings: SpotSettings,
    quiver: Quiver,
    state: ForecastState?,
    load: (Boolean) -> Unit,
    onSettingsChange: (SpotSettings) -> Unit,
    onSpotChange: (Spot) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val askPermission = rememberNotificationPermission()

    LaunchedEffect(spot.id) { load(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(spot.name, maxLines = 1)
                        if (spot.region.isNotBlank()) {
                            Text(spot.region, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    IconButton(onClick = {
                        val on = !spot.alarm
                        onSpotChange(spot.copy(alarm = on))
                        if (on) {
                            askPermission()
                            if (!settings.alarmEnabled) onSettingsChange(settings.copy(alarmEnabled = true))
                            Toast.makeText(context, "Wind-Alarm für ${spot.name} an", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Wind-Alarm aus", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        if (spot.alarm) {
                            Icon(Icons.Filled.Notifications, "Wind-Alarm aus", tint = MaterialTheme.colorScheme.secondary)
                        } else {
                            Icon(Icons.Outlined.Notifications, "Wind-Alarm an")
                        }
                    }
                    IconButton(onClick = { load(true) }) { Icon(Icons.Default.Refresh, "Aktualisieren") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Löschen") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                null, ForecastState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ForecastState.OutOfArea -> Text(
                    "Dieser Spot liegt außerhalb des Vorhersagegebiets. Die Vorhersage von GeoSphere Austria " +
                        "deckt den erweiterten Alpenraum ab (ca. 43–51,5° N, 5–22,5° O).",
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )

                is ForecastState.Error -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { load(true) }) { Text("Nochmal versuchen") }
                }

                is ForecastState.Ok -> {
                    val days = remember(state, settings, quiver) {
                        Advisor.days(state.hours, settings, quiver, from = LocalDateTime.now())
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { SettingsSummary(settings, quiver, state) }
                        days.forEach { day ->
                            item(key = "h${day.date}") { DayHeader(day) }
                            item(key = "c${day.date}") { ColumnHeader(quiver) }
                            items(day.hours, key = { it.hour.time.toString() }) { rec -> HourRow(rec) }
                        }
                        item {
                            Text(
                                GeoSphere.ATTRIBUTION + " · Segel/Board mit Mittelwind + 1/5 des Böenüberschusses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Spot löschen?") },
            text = { Text("${spot.name} wird aus deiner Liste entfernt.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SettingsSummary(settings: SpotSettings, quiver: Quiver, state: ForecastState.Ok) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${settings.weightKg.de(0)} kg · ${settings.level.label} · ${settings.sailType.label}",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Gleitbar ab ${settings.minWindKn.de(0)} kn · Stand ${state.loadedAt.format(TIME)}" +
                    if (quiver.isEmpty) "" else " · Segel/Board aus deinem Material",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DayHeader(day: DaySummary) {
    val good = day.goodHours.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (good) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(dayLabel(day.date), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text(
            if (good) "🏄 ${day.goodHours.size} h gleitbar · ${WindAlarm.windowText(day)}"
            else "Kein Gleitwind (max. ${day.maxWindKn.de(0)} kn" +
                (day.maxGustKn?.let { ", Böen ${it.de(0)}" } ?: "") + ")",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val COL_TIME = Modifier.width(48.dp)
private val COL_WIND = Modifier.width(78.dp)
private val COL_DIR = Modifier.width(64.dp)
private val COL_SAIL = Modifier.width(62.dp)
private val COL_BOARD = Modifier.width(56.dp)

@Composable
private fun ColumnHeader(quiver: Quiver) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.labelSmall
        val c = MaterialTheme.colorScheme.onSurfaceVariant
        Text("Zeit", COL_TIME, style = style, color = c)
        Text("Wind/Böen", COL_WIND, style = style, color = c)
        Text("Richtung", COL_DIR, style = style, color = c)
        Text(if (quiver.sails.isEmpty()) "Segel" else "Dein Segel", COL_SAIL, style = style, color = c)
        Text("Board", COL_BOARD, style = style, color = c)
        Text("Wetter", Modifier.weight(1f), style = style, color = c)
    }
}

@Composable
private fun HourRow(rec: HourRec) {
    val h = rec.hour
    val dim = if (rec.good) 1f else 0.45f
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (rec.good) MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(h.time.format(TIME), COL_TIME, fontWeight = FontWeight.Medium)
            Row(COL_WIND, verticalAlignment = Alignment.CenterVertically) {
                WindBadge(h.windKn)
                h.gustKn?.let {
                    Spacer(Modifier.width(3.dp))
                    WindBadge(it, fontSize = 11.sp)
                }
            }
            Row(COL_DIR, verticalAlignment = Alignment.CenterVertically) {
                h.dirDeg?.let {
                    WindArrow(it, Modifier.size(20.dp))
                    Text(compass(it), fontSize = 12.sp)
                }
            }
            Text(
                if (rec.tooMuch) "zu viel" else "${rec.shownSail.de()} m²",
                COL_SAIL,
                fontWeight = if (rec.good) FontWeight.Bold else FontWeight.Normal,
                color = if (rec.tooMuch) MaterialTheme.colorScheme.error else onSurface.copy(alpha = dim),
                fontSize = 13.sp,
            )
            Text("${rec.shownBoard} L", COL_BOARD, color = onSurface.copy(alpha = dim), fontSize = 13.sp)
            Text(
                buildString {
                    h.tempC?.let { append(it.de(0)).append("°") }
                    val p = h.precipMm ?: 0.0
                    if (p >= 0.1) append(" 💧").append(p.de())
                    else h.cloudPct?.let { append(if (it < 30) " ☀" else if (it < 70) " ⛅" else " ☁") }
                },
                Modifier.weight(1f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

// ---------- Spot hinzufügen ----------

@Composable
fun AddSpotScreen(onBack: () -> Unit, onPick: (GeoResult) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val context = LocalContext.current

    fun search() {
        if (query.isBlank()) return
        loading = true
        error = null
        scope.launch {
            try {
                results = Places.search(context, query)
            } catch (e: Exception) {
                error = e.message ?: "Fehler bei der Suche"
            }
            loading = false
            searched = true
        }
    }

    LaunchedEffect(query) {
        if (query.trim().length >= 3) {
            delay(600)
            search()
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spot hinzufügen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Ort oder See, z. B. Podersdorf") },
                singleLine = true,
                trailingIcon = {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = ::search) { Icon(Icons.Default.Search, "Suchen") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tipp: Für eine genaue Stelle am Wasser kannst du auch Koordinaten eingeben, z. B. 47.855, 16.830 " +
                    "(in Google Maps lange auf die Stelle drücken und kopieren).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            error?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
            if (searched && results.isEmpty() && error == null && !loading) {
                Text("Nichts gefunden – probier den nächsten Ort am Wasser.")
            }
            LazyColumn {
                items(results) { r ->
                    val covered = GeoSphere.covers(r.lat, r.lon)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = covered) { onPick(r) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = if (covered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                r.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (covered) Color.Unspecified else MaterialTheme.colorScheme.outline,
                            )
                            Text(
                                if (covered) "${r.region} · ${r.lat.de(3)}, ${r.lon.de(3)}"
                                else "${r.region} · außerhalb des Vorhersagegebiets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
