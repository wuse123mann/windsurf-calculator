@file:OptIn(ExperimentalLayoutApi::class)

package de.wuse.windsurfrechner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------- Tab „Material“ ----------

@Composable
fun MaterialContent(quiver: Quiver, onChange: (Quiver) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Deine Segel", style = MaterialTheme.typography.titleMedium)
            ItemChips(
                items = quiver.sails.map { it to "${it.de()} m²" },
                empty = "Noch keine Segel eingetragen",
                onRemove = { s -> onChange(quiver.copy(sails = quiver.sails - s)) },
            )
            AddField(label = "Segelgröße", unit = "m²", decimals = true) { v ->
                val size = (v * 10).roundToInt() / 10.0
                if (size in 1.0..14.0) {
                    onChange(quiver.copy(sails = (quiver.sails + size).distinct().sorted()))
                    true
                } else {
                    false
                }
            }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Deine Boards", style = MaterialTheme.typography.titleMedium)
            ItemChips(
                items = quiver.boards.map { it to "$it L" },
                empty = "Noch keine Boards eingetragen",
                onRemove = { b -> onChange(quiver.copy(boards = quiver.boards - b)) },
            )
            AddField(label = "Volumen", unit = "L", decimals = false) { v ->
                val volume = v.roundToInt()
                if (volume in 40..300) {
                    onChange(quiver.copy(boards = (quiver.boards + volume).distinct().sorted()))
                    true
                } else {
                    false
                }
            }
        }
    }

    Text(
        "Ist dein Material eingetragen, zeigen der Rechner und die Spot-Vorhersage zusätzlich, welches " +
            "deiner Segel und Boards am besten passt.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun <T> ItemChips(items: List<Pair<T, String>>, empty: String, onRemove: (T) -> Unit) {
    if (items.isEmpty()) {
        Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (value, label) ->
            InputChip(
                selected = false,
                onClick = { onRemove(value) },
                label = { Text(label, fontWeight = FontWeight.SemiBold) },
                trailingIcon = { Icon(Icons.Default.Close, "Entfernen") },
            )
        }
    }
}

/** Eingabefeld mit „Hinzufügen“; [onAdd] liefert false, wenn der Wert nicht passt. */
@Composable
private fun AddField(label: String, unit: String, decimals: Boolean, onAdd: (Double) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun submit() {
        val v = text.replace(',', '.').toDoubleOrNull()
        if (v != null && onAdd(v)) {
            text = ""
            error = false
        } else {
            error = true
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new.filter { it.isDigit() || (decimals && (it == ',' || it == '.')) }.take(5)
                error = false
            },
            label = { Text(label) },
            suffix = { Text(unit) },
            singleLine = true,
            isError = error,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        FilledTonalButton(onClick = ::submit, enabled = text.isNotBlank()) { Text("Hinzufügen") }
    }
}

/** Kleine Zeile im Rechner: welches eigene Teil am besten passt. */
@Composable
fun MyGearCard(quiver: Quiver, sailM2: Double, volumeL: Int) {
    val sail = quiver.pickSail(sailM2)
    val board = quiver.pickBoard(volumeL)
    if (sail == null && board == null) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎒", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Aus deinem Material: " + listOfNotNull(
                    sail?.let { "Segel ${it.de()} m²" },
                    board?.let { "Board $it L" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------- Premium-Karte ----------

@Composable
fun PremiumCard() {
    val context = LocalContext.current
    val price by Premium.price.collectAsState()
    val message by Premium.message.collectAsState()

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("⭐ Premium", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Einmal kaufen, für immer nutzen – kein Abo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            listOf(
                "🌬️" to "Deine Spots mit stündlicher Vorhersage für 2½ Tage – Wind, Böen, Richtung",
                "⛵" to "Zu jeder Stunde die passende Segel- und Boardgröße",
                "🔔" to "Wind-Alarm: Benachrichtigung, sobald an deinem Spot Gleitwind kommt",
                "🎒" to "Dein eigenes Material – die App sagt dir, welches Segel du aufriggen sollst",
            ).forEach { (icon, text) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(icon, fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = { Premium.buy(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(price?.let { "Freischalten für $it" } ?: "Freischalten")
            }
            TextButton(onClick = { Premium.restore(context) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Kauf wiederherstellen")
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Rechner und Umrechner bleiben kostenlos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
