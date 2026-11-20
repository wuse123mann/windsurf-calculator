package de.wuse.windsurfrechner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    /** Spot, der per Tipp auf eine Wind-Alarm-Benachrichtigung geöffnet werden soll. */
    private val openSpot = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = Storage(applicationContext)
        Premium.init(applicationContext)
        if (Premium.isUnlocked(applicationContext) && storage.loadSpotSettings().alarmEnabled) {
            WindAlarm.schedule(applicationContext, true)
        }
        openSpot.value = intent?.getStringExtra(WindAlarm.EXTRA_SPOT)
        setContent {
            WindsurfTheme {
                App(storage, openSpot.value) { openSpot.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(WindAlarm.EXTRA_SPOT)?.let { openSpot.value = it }
    }
}

// ---------- Theme ----------

private val Ocean = Color(0xFF0A6E9E)
private val DeepOcean = Color(0xFF064A6E)
private val Coral = Color(0xFFFF7A45)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Ocean,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE9F7),
    onPrimaryContainer = Color(0xFF02344E),
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0D3),
    onSecondaryContainer = Color(0xFF5A1D00),
    tertiaryContainer = Color(0xFFD3F5DC),
    onTertiaryContainer = Color(0xFF0B3A19),
    background = Color(0xFFF3F8FB),
    surface = Color(0xFFF3F8FB),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF7CCBF0),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF0B4D6C),
    onPrimaryContainer = Color(0xFFCDE9F7),
    secondary = Color(0xFFFFB494),
    onSecondary = Color(0xFF5A1D00),
    secondaryContainer = Color(0xFF7A2E0C),
    onSecondaryContainer = Color(0xFFFFE0D3),
    tertiaryContainer = Color(0xFF1C5A30),
    onTertiaryContainer = Color(0xFFD3F5DC),
    background = Color(0xFF0D1A21),
    surface = Color(0xFF0D1A21),
)

@Composable
fun WindsurfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

// ---------- Navigation & Zustand ----------

sealed interface Screen {
    data object Main : Screen
    data object AddSpot : Screen
    data class Detail(val spotId: String) : Screen
}

private const val TAB_CALC = 0
private const val TAB_CONVERTER = 1
private const val TAB_SPOTS = 2
private const val TAB_MATERIAL = 3

@Composable
fun App(storage: Storage, openSpotId: String?, onSpotOpened: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val unlocked by Premium.unlocked.collectAsState()

    // ----- Rechner -----
    var weight by rememberSaveable { mutableFloatStateOf(storage.weight()) }
    var wind by rememberSaveable { mutableFloatStateOf(storage.windKnots()) }
    var level by rememberSaveable { mutableStateOf(storage.level()) }
    var windUnit by rememberSaveable { mutableStateOf(storage.windUnit()) }
    var sailType by rememberSaveable { mutableStateOf(storage.sailType()) }

    LaunchedEffect(weight, wind, level, windUnit, sailType) {
        storage.saveCalculator(weight, wind, level, windUnit, sailType)
    }

    // ----- Spots, Alarm, Material (Premium) -----
    val spots = remember { mutableStateListOf<Spot>().apply { addAll(storage.loadSpots()) } }
    var spotPrefs by remember { mutableStateOf(storage.loadSpotSettings()) }
    var quiver by remember { mutableStateOf(storage.loadQuiver()) }
    val forecasts = remember { mutableStateMapOf<String, ForecastState>() }
    val settings = spotPrefs.copy(weightKg = weight.toDouble(), level = level, sailType = sailType)

    var tab by rememberSaveable { mutableIntStateOf(TAB_CALC) }
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Main)) }

    fun navigate(s: Screen) { backStack = backStack + s }
    fun back() { if (backStack.size > 1) backStack = backStack.dropLast(1) }
    fun replaceTop(s: Screen) { backStack = backStack.dropLast(1) + s }

    /** Lädt die Vorhersage – alle Spots in einer Anfrage; frische Daten (< 30 min) werden behalten. */
    fun loadForecasts(force: Boolean, only: Spot? = null) {
        val now = LocalDateTime.now()
        val toLoad = (if (only != null) listOf(only) else spots.toList()).filter { s ->
            if (!s.covered) {
                forecasts[s.id] = ForecastState.OutOfArea
                false
            } else {
                val cur = forecasts[s.id]
                val fresh = cur is ForecastState.Ok && cur.loadedAt.isAfter(now.minusMinutes(30))
                force || !(cur is ForecastState.Loading || fresh)
            }
        }
        if (toLoad.isEmpty()) return
        toLoad.forEach { forecasts[it.id] = ForecastState.Loading }
        scope.launch {
            try {
                val result = GeoSphere.forecast(toLoad.map { it.lat to it.lon })
                val stamp = LocalDateTime.now()
                toLoad.zip(result).forEach { (s, hours) -> forecasts[s.id] = ForecastState.Ok(hours, stamp) }
            } catch (e: Exception) {
                val msg = if (e is UnknownHostException) "Keine Internetverbindung" else e.message ?: "Fehler beim Laden"
                toLoad.forEach { forecasts[it.id] = ForecastState.Error(msg) }
            }
        }
    }

    fun updateSpotSettings(s: SpotSettings) {
        val alarmChanged = s.alarmEnabled != spotPrefs.alarmEnabled
        spotPrefs = s
        storage.saveSpotSettings(s)
        if (alarmChanged) WindAlarm.schedule(context, s.alarmEnabled)
    }

    fun updateSpot(s: Spot) {
        val i = spots.indexOfFirst { it.id == s.id }
        if (i >= 0) spots[i] = s else spots.add(s)
        storage.saveSpots(spots.toList())
    }

    fun updateQuiver(q: Quiver) {
        quiver = q
        storage.saveQuiver(q)
    }

    LaunchedEffect(openSpotId, unlocked) {
        if (openSpotId != null) {
            if (unlocked && spots.any { it.id == openSpotId }) {
                tab = TAB_SPOTS
                backStack = listOf(Screen.Main, Screen.Detail(openSpotId))
            }
            onSpotOpened()
        }
    }

    BackHandler(enabled = backStack.size > 1) { back() }

    when (val screen = backStack.last()) {
        Screen.Main -> {
            val result = remember(weight, wind, level, sailType) {
                WindsurfCalculator.calculate(weight.toDouble(), wind.toDouble(), level, sailType)
            }
            val table = remember(weight, level, sailType) {
                WindsurfCalculator.table(weight.toDouble(), level, sailType)
            }

            Scaffold { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Header()

                    TabRow(
                        selectedTabIndex = tab,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)),
                    ) {
                        Tab(selected = tab == TAB_CALC, onClick = { tab = TAB_CALC }, text = { Text("Rechner") })
                        Tab(selected = tab == TAB_CONVERTER, onClick = { tab = TAB_CONVERTER }, text = { Text("Umrechner") })
                        Tab(selected = tab == TAB_SPOTS, onClick = { tab = TAB_SPOTS }, text = { Text("Spots") })
                        Tab(selected = tab == TAB_MATERIAL, onClick = { tab = TAB_MATERIAL }, text = { Text("Material") })
                    }

                    when (tab) {
                        TAB_CONVERTER -> ConverterContent(initialKnots = wind) { kn ->
                            wind = kn.coerceIn(
                                WindsurfCalculator.MIN_WIND.toFloat(),
                                WindsurfCalculator.MAX_WIND.toFloat(),
                            )
                            tab = TAB_CALC
                        }

                        TAB_SPOTS -> if (unlocked) {
                            SpotsContent(
                                spots = spots,
                                settings = settings,
                                quiver = quiver,
                                forecastFor = { forecasts[it.id] },
                                onLoad = { force -> loadForecasts(force) },
                                onOpen = { navigate(Screen.Detail(it.id)) },
                                onAdd = { navigate(Screen.AddSpot) },
                                onMove = { from, to ->
                                    if (to in spots.indices) {
                                        val s = spots.removeAt(from)
                                        spots.add(to, s)
                                        storage.saveSpots(spots.toList())
                                    }
                                },
                                onSettingsChange = ::updateSpotSettings,
                            )
                        } else {
                            PremiumCard()
                        }

                        TAB_MATERIAL -> if (unlocked) {
                            MaterialContent(quiver, ::updateQuiver)
                        } else {
                            PremiumCard()
                        }

                        else -> {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    NumberInput(
                                        title = "Dein Gewicht",
                                        unit = "kg",
                                        value = weight,
                                        min = WindsurfCalculator.MIN_WEIGHT.toFloat(),
                                        max = WindsurfCalculator.MAX_WEIGHT.toFloat(),
                                        onChange = { weight = it },
                                        supporting = "inkl. Neoprenanzug",
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        WindUnitPicker(windUnit) { windUnit = it }
                                        NumberInput(
                                            title = "Windstärke",
                                            unit = windUnit.label,
                                            value = windUnit.fromKnots(wind.toDouble()).toFloat(),
                                            min = windUnit.min.toFloat(),
                                            max = windUnit.max.toFloat(),
                                            onChange = { wind = windUnit.toKnots(it.toDouble()).toFloat() },
                                            supporting = "${wind.toDouble().de(0)} kn · ${result.windKmh.de(0)} km/h · " +
                                                "${result.windMs.de(1)} m/s · ${result.beaufort} Bft (${result.beaufortName})",
                                        )
                                    }
                                    LevelPicker(level) { level = it }
                                    SailTypePicker(sailType) { sailType = it }
                                }
                            }

                            ResultCards(result, sailType)

                            if (unlocked) MyGearCard(quiver, result.sailM2, result.volumeL)

                            result.hints.forEach { HintCard(it) }

                            SailTable(table, wind.roundToInt(), weight.roundToInt(), sailType)

                            Text(
                                "Richtwerte nach gängigen Segel- und Boardtabellen für gleitfähige Bedingungen. " +
                                    "Revier, Böigkeit, Material und Fahrstil verschieben die ideale Größe.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        Screen.AddSpot -> AddSpotScreen(
            onBack = ::back,
            onPick = { geo ->
                val spot = Spot(name = geo.name, region = geo.region, lat = geo.lat, lon = geo.lon)
                updateSpot(spot)
                replaceTop(Screen.Detail(spot.id))
            },
        )

        is Screen.Detail -> {
            val spot = spots.firstOrNull { it.id == screen.spotId }
            if (spot == null) {
                LaunchedEffect(screen) { back() }
            } else {
                SpotDetailScreen(
                    spot = spot,
                    settings = settings,
                    quiver = quiver,
                    state = forecasts[spot.id],
                    load = { force -> loadForecasts(force, only = spot) },
                    onSettingsChange = ::updateSpotSettings,
                    onSpotChange = ::updateSpot,
                    onDelete = {
                        spots.removeAll { it.id == spot.id }
                        storage.saveSpots(spots.toList())
                        forecasts.remove(spot.id)
                        back()
                    },
                    onBack = ::back,
                )
            }
        }
    }
}

// ---------- Rechner ----------

@Composable
private fun Header() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(DeepOcean, Ocean, Color(0xFF2BA4C9))),
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column {
            Text(
                "Windsurf-Rechner",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Segelgröße, Wind-Umrechner, Spots & Wind-Alarm",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NumberInput(
    title: String,
    unit: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
    supporting: String,
) {
    var text by remember { mutableStateOf(value.roundToInt().toString()) }
    // Text nachziehen, wenn der Wert über Slider oder Knöpfe geändert wurde
    LaunchedEffect(value, unit) {
        val shown = text.replace(',', '.').toFloatOrNull()
        if (shown == null || abs(shown - value) >= 0.5f) text = value.roundToInt().toString()
    }
    val parsed = text.replace(',', '.').toFloatOrNull()
    val isError = parsed == null || parsed < min || parsed > max

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() || it == ',' || it == '.' }.take(5)
                    text = filtered
                    filtered.replace(',', '.').toFloatOrNull()?.let { v ->
                        if (v in min..max) onChange(v)
                    }
                },
                singleLine = true,
                isError = isError,
                suffix = { Text(unit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.End),
                modifier = Modifier.width(130.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") { onChange((value - 1f).coerceIn(min, max).roundToInt().toFloat()) }
            Slider(
                value = value.coerceIn(min, max),
                onValueChange = { onChange(it.roundToInt().toFloat()) },
                valueRange = min..max,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            StepButton("+") { onChange((value + 1f).coerceIn(min, max).roundToInt().toFloat()) }
        }
        Text(
            if (isError) "Bitte ${min.roundToInt()}–${max.roundToInt()} $unit eingeben" else supporting,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WindUnitPicker(selected: WindUnit, onSelect: (WindUnit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Einheit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        WindUnit.entries.forEach { u ->
            FilterChip(
                selected = u == selected,
                onClick = { onSelect(u) },
                label = { Text(u.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SailTypePicker(selected: SailType, onSelect: (SailType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Segeltyp", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SailType.entries.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = {
                        Text(type.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            selected.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LevelPicker(selected: Level, onSelect: (Level) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Dein Können", style = MaterialTheme.typography.titleMedium)
        Level.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { lvl ->
                    FilterChip(
                        selected = lvl == selected,
                        onClick = { onSelect(lvl) },
                        label = {
                            Text(lvl.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            selected.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultCards(r: CalculationResult, sailType: SailType) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigResult(
            title = if (sailType == SailType.CAMBER) "Segel mit Camber" else "Segel",
            value = "${r.sailM2.de()} m²",
            sub = "${r.sailMinM2.de()}–${r.sailMaxM2.de()} m²",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        BigResult(
            title = "Board",
            value = "${r.volumeL} L",
            sub = "${r.volumeMinL}–${r.volumeMaxL} L",
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailRow("Boardtyp", r.boardType)
            HorizontalDivider()
            DetailRow("Boardbreite", "ca. ${r.boardWidthCm} cm")
            HorizontalDivider()
            DetailRow("Finne", "ca. ${r.finCm} cm")
        }
    }
}

@Composable
private fun BigResult(
    title: String,
    value: String,
    sub: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun HintCard(hint: Hint) {
    val colors = if (hint.isWarning) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text(if (hint.isWarning) "⚠️" else "💡", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(hint.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SailTable(rows: List<TableRow>, currentWind: Int, weight: Int, sailType: SailType) {
    val nearest = rows.minByOrNull { abs(it.windKnots - currentWind) }?.windKnots
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Übersicht für $weight kg" + if (sailType == SailType.CAMBER) " · Camber" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Welches Segel und Board bei welchem Wind",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            TableLine("Wind", "Bft", "Segel", "Board", header = true, highlight = false)
            HorizontalDivider()
            rows.forEach { row ->
                TableLine(
                    "${row.windKnots} kn",
                    "${WindsurfCalculator.beaufort(row.windKnots.toDouble())}",
                    "${row.sailM2.de()} m²",
                    "${row.volumeL} L",
                    header = false,
                    highlight = row.windKnots == nearest,
                )
            }
        }
    }
}

@Composable
private fun TableLine(a: String, b: String, c: String, d: String, header: Boolean, highlight: Boolean) {
    val weight = if (header || highlight) FontWeight.Bold else FontWeight.Normal
    val color = when {
        highlight -> MaterialTheme.colorScheme.onPrimaryContainer
        header -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .padding(vertical = 7.dp, horizontal = 8.dp),
    ) {
        Text(a, Modifier.weight(1f), fontWeight = weight, color = color)
        Text(b, Modifier.weight(0.6f), fontWeight = weight, color = color, textAlign = TextAlign.Center)
        Text(c, Modifier.weight(1f), fontWeight = weight, color = color, textAlign = TextAlign.End)
        Text(d, Modifier.weight(1f), fontWeight = weight, color = color, textAlign = TextAlign.End)
    }
}
