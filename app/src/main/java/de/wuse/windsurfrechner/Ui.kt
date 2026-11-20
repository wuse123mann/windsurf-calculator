package de.wuse.windsurfrechner

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun Double.de(decimals: Int = 1): String = String.format(Locale.GERMANY, "%.${decimals}f", this)

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d.M.", Locale.GERMAN)
private val WEEKDAY_FORMAT = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Heute"
    today.plusDays(1) -> "Morgen"
    else -> date.format(DAY_FORMAT)
}

fun weekdayShort(date: LocalDate): String = date.format(WEEKDAY_FORMAT).trimEnd('.')

private val COMPASS = listOf(
    "N", "NNO", "NO", "ONO", "O", "OSO", "SO", "SSO",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
)

fun compass(deg: Int): String = COMPASS[(((deg % 360) + 360) % 360 * 16 + 180) / 360 % 16]

/** Farbskala für Wind in Knoten (von ruhig bis stürmisch). */
fun windColor(kn: Double): Color = when {
    kn < 5 -> Color(0xFFEAF6FF)
    kn < 8 -> Color(0xFFB8E3FF)
    kn < 11 -> Color(0xFF9BE8C0)
    kn < 14 -> Color(0xFF4CD964)
    kn < 17 -> Color(0xFFC9F04A)
    kn < 20 -> Color(0xFFFFE14D)
    kn < 24 -> Color(0xFFFFA94D)
    kn < 28 -> Color(0xFFFF6B4D)
    kn < 33 -> Color(0xFFE8405A)
    else -> Color(0xFFC04BD8)
}

@Composable
fun WindBadge(kn: Double, modifier: Modifier = Modifier, fontSize: TextUnit = 14.sp) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(windColor(kn))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            kn.de(0),
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
        )
    }
}

/** Pfeil zeigt, wohin der Wind weht. */
@Composable
fun WindArrow(deg: Int, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        "↑",
        modifier = modifier.rotate((deg + 180f) % 360f),
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = color,
    )
}

/** Liefert eine Funktion, die (ab Android 13) die Benachrichtigungs-Berechtigung anfragt. */
@Composable
fun rememberNotificationPermission(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !WindAlarm.hasPermission(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
