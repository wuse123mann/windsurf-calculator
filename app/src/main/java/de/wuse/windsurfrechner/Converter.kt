package de.wuse.windsurfrechner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Offizielle Beaufort-Bereiche (Deutscher Wetterdienst). */
private data class BftRange(val kn: String, val kmh: String, val ms: String)

private val BFT_RANGES = listOf(
    BftRange("< 1", "< 1", "0–0,2"),
    BftRange("1–3", "1–5", "0,3–1,5"),
    BftRange("4–6", "6–11", "1,6–3,3"),
    BftRange("7–10", "12–19", "3,4–5,4"),
    BftRange("11–16", "20–28", "5,5–7,9"),
    BftRange("17–21", "29–38", "8,0–10,7"),
    BftRange("22–27", "39–49", "10,8–13,8"),
    BftRange("28–33", "50–61", "13,9–17,1"),
    BftRange("34–40", "62–74", "17,2–20,7"),
    BftRange("41–47", "75–88", "20,8–24,4"),
    BftRange("48–55", "89–102", "24,5–28,4"),
    BftRange("56–63", "103–117", "28,5–32,6"),
    BftRange("≥ 64", "≥ 118", "≥ 32,7"),
)

private fun formatInput(value: Double): String =
    if (abs(value - value.roundToInt()) < 0.05) value.roundToInt().toString() else value.de(1)

@Composable
fun ConverterContent(initialKnots: Float, onUseInCalculator: (Float) -> Unit) {
    var unit by rememberSaveable { mutableStateOf(WindUnit.KNOTEN) }
    var knots by rememberSaveable { mutableFloatStateOf(initialKnots) }
    var text by rememberSaveable { mutableStateOf(formatInput(initialKnots.toDouble())) }

    val parsed = text.replace(',', '.').toDoubleOrNull()
    val isError = parsed == null || parsed < 0 || parsed > unit.converterMax
    val bft = if (unit == WindUnit.BFT) {
        (parsed ?: 0.0).roundToInt().coerceIn(0, 12)
    } else {
        WindsurfCalculator.beaufort(knots.toDouble())
    }

    fun setFromInput(raw: String) {
        val filtered = if (unit == WindUnit.BFT) raw.filter { it.isDigit() }.take(2)
        else raw.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
        text = filtered
        filtered.replace(',', '.').toDoubleOrNull()?.let { v ->
            if (v >= 0 && v <= unit.converterMax) knots = unit.toKnots(v).toFloat()
        }
    }

    // ---------- Eingabe ----------
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Wind umrechnen", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WindUnit.entries.forEach { u ->
                    FilterChip(
                        selected = u == unit,
                        onClick = {
                            unit = u
                            text = formatInput(u.fromKnots(knots.toDouble()))
                        },
                        label = { Text(u.label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { setFromInput(it) },
                singleLine = true,
                isError = isError,
                label = { Text("Wert in ${unit.label}") },
                suffix = { Text(unit.label) },
                supportingText = {
                    if (isError) Text("Bitte 0–${unit.converterMax.roundToInt()} ${unit.label} eingeben")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (unit == WindUnit.BFT) KeyboardType.Number else KeyboardType.Decimal,
                ),
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            if (unit == WindUnit.BFT) {
                Slider(
                    value = bft.toFloat(),
                    onValueChange = { setFromInput(it.roundToInt().toString()) },
                    valueRange = 0f..12f,
                    steps = 11,
                )
            }
        }
    }

    // ---------- Ergebnis ----------
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "$bft Beaufort – ${WindsurfCalculator.beaufortName(bft)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (unit == WindUnit.BFT) {
                val r = BFT_RANGES[bft]
                ConvRow("Knoten", "${r.kn} kn")
                HorizontalDivider()
                ConvRow("Kilometer pro Stunde", "${r.kmh} km/h")
                HorizontalDivider()
                ConvRow("Meter pro Sekunde", "${r.ms} m/s")
            } else {
                val kn = knots.toDouble()
                ConvRow("Knoten", "${kn.de(1)} kn", unit == WindUnit.KNOTEN)
                HorizontalDivider()
                ConvRow("Kilometer pro Stunde", "${(kn * 1.852).de(1)} km/h", unit == WindUnit.KMH)
                HorizontalDivider()
                ConvRow("Meter pro Sekunde", "${(kn * 0.5144).de(1)} m/s", unit == WindUnit.MS)
                HorizontalDivider()
                ConvRow("Meilen pro Stunde", "${(kn * 1.1508).de(1)} mph")
            }
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = { onUseInCalculator(knots) },
                enabled = !isError,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Im Rechner verwenden")
            }
            if (knots < WindsurfCalculator.MIN_WIND || knots > WindsurfCalculator.MAX_WIND) {
                Text(
                    "Der Rechner arbeitet mit 5–45 Knoten; der Wert wird angepasst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ---------- Beaufort-Tabelle ----------
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Beaufort-Skala", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            BftLine("Bft", "Bezeichnung", "kn", "km/h", header = true, highlight = false)
            HorizontalDivider()
            BFT_RANGES.forEachIndexed { i, r ->
                BftLine("$i", WindsurfCalculator.beaufortName(i), r.kn, r.kmh, header = false, highlight = i == bft)
            }
        }
    }
}

@Composable
private fun ConvRow(label: String, value: String, isInput: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = if (isInput) FontWeight.Normal else FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun BftLine(a: String, b: String, c: String, d: String, header: Boolean, highlight: Boolean) {
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
            .padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(a, Modifier.weight(0.4f), fontWeight = weight, color = color)
        Text(b, Modifier.weight(1.6f), fontWeight = weight, color = color, style = MaterialTheme.typography.bodyMedium)
        Text(c, Modifier.weight(0.8f), fontWeight = weight, color = color, textAlign = TextAlign.End)
        Text(d, Modifier.weight(0.9f), fontWeight = weight, color = color, textAlign = TextAlign.End)
    }
}
