package de.wuse.windsurfrechner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ---------- Modelle ----------

data class Hour(
    /** Ortszeit des Geräts. */
    val time: LocalDateTime,
    val windKn: Double,
    val gustKn: Double?,
    /** Richtung, aus der der Wind kommt (0 = Nord). */
    val dirDeg: Int?,
    val tempC: Double?,
    val precipMm: Double?,
    val cloudPct: Int?,
    val isDay: Boolean?,
)

/** Einstellungen für Vorhersage und Wind-Alarm (Gewicht, Können und Segeltyp kommen aus dem Rechner). */
data class SpotSettings(
    val weightKg: Double = 80.0,
    val level: Level = Level.FORTGESCHRITTEN,
    val sailType: SailType = SailType.OHNE_CAMBER,
    /** Ab diesem Wind (kn, böenbereinigt) zählt eine Stunde als „gleitbar“. */
    val minWindKn: Double = 12.0,
    val onlyDaylight: Boolean = true,
    val alarmEnabled: Boolean = false,
    /** Wie viele Tage voraus der Alarm schaut (1 = nur heute, 2 = heute + morgen, 3 = bis übermorgen). */
    val alarmDays: Int = 2,
    /** Mindestanzahl gleitbarer Stunden an einem Tag, damit der Alarm auslöst. */
    val alarmMinHours: Int = 2,
)

/** Eigenes Material: Segelgrößen (m²) und Boardvolumen (L). */
data class Quiver(
    val sails: List<Double> = emptyList(),
    val boards: List<Int> = emptyList(),
) {
    val isEmpty get() = sails.isEmpty() && boards.isEmpty()

    /** Das eigene Segel, das der Empfehlung am nächsten kommt (bei Gleichstand das kleinere). */
    fun pickSail(recommended: Double): Double? =
        sails.minWithOrNull(compareBy<Double>({ abs(it - recommended) }, { it }))

    /** Das eigene Board, das der Empfehlung am nächsten kommt (bei Gleichstand das größere). */
    fun pickBoard(recommended: Int): Int? =
        boards.minWithOrNull(compareBy<Int>({ abs(it - recommended) }, { -it }))
}

// ---------- GeoSphere Austria (staatlicher Wetterdienst, offene Daten CC BY 4.0) ----------

/**
 * Vorhersage aus dem GeoSphere-Austria-Modell C-LAEF AlpeAdria (1 km, stündlich, ca. 60 Stunden).
 * Die Daten stehen unter CC BY 4.0 und dürfen mit Quellenangabe auch kommerziell genutzt werden.
 * Kein Konto und kein Schlüssel nötig. Limit: 5 Anfragen/Sekunde, 240 pro Stunde.
 */
object GeoSphere {
    private const val ENDPOINT = "https://dataset.api.hub.geosphere.at/v1/timeseries/forecast/nwp-v2-1h-1km"
    private const val PARAMETERS = "10u,10v,10fg,2t,tp,tcc"
    private const val MS_TO_KN = 1.943844

    const val ATTRIBUTION = "Vorhersage: GeoSphere Austria, Modell C-LAEF 1 km (CC BY 4.0)"

    /** Gebiet des Modells: erweiterter Alpenraum (ca. Lyon – Budapest, Florenz – Würzburg). */
    fun covers(lat: Double, lon: Double): Boolean = lat in 43.0..51.5 && lon in 5.03..22.57

    /** Lädt die Vorhersage für mehrere Orte mit einer einzigen Anfrage. Reihenfolge wie [points]. */
    suspend fun forecast(
        points: List<Pair<Double, Double>>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<List<Hour>> = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext emptyList()
        parse(get(url(points)), points, zone)
    }

    fun url(points: List<Pair<Double, Double>>): String = buildString {
        append(ENDPOINT)
        append("?parameters=").append(PARAMETERS)
        append("&output_format=geojson")
        points.forEach { (lat, lon) ->
            append("&lat_lon=")
            append(String.format(Locale.US, "%.4f,%.4f", lat, lon))
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 25_000
        conn.setRequestProperty("User-Agent", "Windsurf-Rechner-Android")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code == 429) throw IOException("Zu viele Anfragen – bitte in ein paar Minuten nochmal")
            if (code !in 200..299) throw IOException("Wetterdienst antwortet mit Fehler $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun parseTime(s: String): Instant =
        runCatching { OffsetDateTime.parse(s).toInstant() }
            .recoverCatching { Instant.parse(s) }
            .getOrElse { LocalDateTime.parse(s.removeSuffix("Z")).toInstant(ZoneOffset.UTC) }

    /** Windrichtung (woher) aus den Komponenten u (nach Osten) und v (nach Norden). */
    fun direction(u: Double, v: Double): Int {
        if (u == 0.0 && v == 0.0) return 0
        val deg = Math.toDegrees(atan2(-u, -v))
        return ((deg + 360.0) % 360.0).toInt()
    }

    fun parse(json: String, points: List<Pair<Double, Double>>, zone: ZoneId): List<List<Hour>> {
        val root = JSONObject(json)
        val stamps = root.getJSONArray("timestamps")
        val times = (0 until stamps.length()).map { parseTime(stamps.getString(it)) }
        val features = root.getJSONArray("features")
        if (features.length() != points.size) {
            throw IOException("Unerwartete Antwort vom Wetterdienst")
        }
        return (0 until features.length()).map { f ->
            val params = features.getJSONObject(f).getJSONObject("properties").getJSONObject("parameters")
            val (lat, lon) = points[f]
            fun series(key: String): JSONArray? = params.optJSONObject(key)?.optJSONArray("data")
            val u = series("10u")
            val v = series("10v")
            val gust = series("10fg")
            val temp = series("2t")
            val precip = series("tp")
            val cloud = series("tcc")
            times.indices.mapNotNull { i ->
                val uu = u.num(i) ?: return@mapNotNull null
                val vv = v.num(i) ?: return@mapNotNull null
                val speedKn = sqrt(uu * uu + vv * vv) * MS_TO_KN
                // tp ist seit Modellstart aufsummiert → Stundenwert = Differenz zur Vorstunde
                val tpNow = precip.num(i)
                val tpPrev = if (i > 0) precip.num(i - 1) else null
                val hourPrecip = if (tpNow != null && tpPrev != null) (tpNow - tpPrev).coerceAtLeast(0.0) else null
                val t = temp.num(i)?.let { if (it > 150) it - 273.15 else it }
                val c = cloud.num(i)?.let { if (it <= 1.5) it * 100 else it }
                Hour(
                    time = LocalDateTime.ofInstant(times[i], zone),
                    windKn = speedKn,
                    gustKn = gust.num(i)?.let { it * MS_TO_KN },
                    dirDeg = direction(uu, vv),
                    tempC = t,
                    precipMm = hourPrecip,
                    cloudPct = c?.toInt()?.coerceIn(0, 100),
                    isDay = Sun.isDay(lat, lon, times[i]),
                )
            }
        }
    }

    private fun JSONArray?.num(i: Int): Double? {
        if (this == null || i >= length() || isNull(i)) return null
        return optDouble(i).takeUnless { it.isNaN() }
    }
}

/** Sonnenstand (vereinfachte Formel, auf etwa eine Minute genau) – für „nur bei Tageslicht“. */
object Sun {
    fun elevation(lat: Double, lon: Double, t: Instant): Double {
        val n = t.epochSecond / 86400.0 + 2440587.5 - 2451545.0
        val meanLon = (280.460 + 0.9856474 * n).mod(360.0)
        val g = Math.toRadians((357.528 + 0.9856003 * n).mod(360.0))
        val lambda = Math.toRadians(meanLon + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val eps = Math.toRadians(23.439 - 0.0000004 * n)
        val ra = atan2(cos(eps) * sin(lambda), cos(lambda))
        val dec = asin(sin(eps) * sin(lambda))
        val gmstHours = (18.697374558 + 24.06570982441908 * n).mod(24.0)
        val hourAngle = Math.toRadians((gmstHours * 15 + lon).mod(360.0)) - ra
        val phi = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(hourAngle)))
    }

    /** Hell genug zum Surfen: Sonne über dem Horizont (inkl. Lichtbrechung). */
    fun isDay(lat: Double, lon: Double, t: Instant): Boolean = elevation(lat, lon, t) > -0.833
}

// ---------- Auswertung: Segel pro Stunde ----------

data class HourRec(
    val hour: Hour,
    /** Wind, mit dem gerechnet wird: Mittelwind plus ein Fünftel des Böenüberschusses. */
    val effectiveKn: Double,
    val sailM2: Double,
    val volumeL: Int,
    /** Passendes Segel/Board aus dem eigenen Material, falls hinterlegt. */
    val mySail: Double?,
    val myBoard: Int?,
    val planing: Boolean,
    val tooMuch: Boolean,
) {
    val good get() = planing && !tooMuch
    val shownSail get() = mySail ?: sailM2
    val shownBoard get() = myBoard ?: volumeL
}

data class DaySummary(
    val date: LocalDate,
    val hours: List<HourRec>,
    val goodHours: List<HourRec>,
    val maxWindKn: Double,
    val maxGustKn: Double?,
) {
    val sailMin get() = goodHours.minOfOrNull { it.shownSail }
    val sailMax get() = goodHours.maxOfOrNull { it.shownSail }
    val firstGood get() = goodHours.firstOrNull()?.hour?.time?.hour
    val lastGood get() = goodHours.lastOrNull()?.hour?.time?.hour
}

object Advisor {
    fun maxWindFor(level: Level): Double = when (level) {
        Level.EINSTEIGER -> 12.0
        Level.AUFSTEIGER -> 22.0
        Level.FORTGESCHRITTEN -> 32.0
        Level.PROFI -> 40.0
    }

    fun effectiveWind(h: Hour): Double {
        val gustExtra = ((h.gustKn ?: h.windKn) - h.windKn).coerceAtLeast(0.0)
        return h.windKn + 0.2 * gustExtra
    }

    fun recommend(h: Hour, s: SpotSettings, quiver: Quiver = Quiver()): HourRec {
        val eff = effectiveWind(h)
        val calcWind = eff.coerceIn(WindsurfCalculator.MIN_WIND, WindsurfCalculator.MAX_WIND)
        val minWind = if (s.level == Level.EINSTEIGER) minOf(s.minWindKn, 5.0) else s.minWindKn
        val sail = WindsurfCalculator.sailSize(s.weightKg, calcWind, s.level, s.sailType)
        val volume = WindsurfCalculator.boardVolume(s.weightKg, calcWind, s.level)
        return HourRec(
            hour = h,
            effectiveKn = eff,
            sailM2 = sail,
            volumeL = volume,
            mySail = quiver.pickSail(sail),
            myBoard = quiver.pickBoard(volume),
            planing = eff >= minWind,
            tooMuch = eff > maxWindFor(s.level),
        )
    }

    private fun isDaylight(h: Hour): Boolean = h.isDay ?: (h.time.hour in 7..19)

    fun days(
        hours: List<Hour>,
        s: SpotSettings,
        quiver: Quiver = Quiver(),
        from: LocalDateTime? = null,
    ): List<DaySummary> =
        hours
            .filter { from == null || !it.time.isBefore(from.withMinute(0).withSecond(0).withNano(0)) }
            .groupBy { it.time.toLocalDate() }
            .map { (date, list) ->
                val daylight = list.filter(::isDaylight)
                val shown = if (s.onlyDaylight) daylight else list
                DaySummary(
                    date = date,
                    hours = shown.map { recommend(it, s, quiver) },
                    goodHours = daylight.map { recommend(it, s, quiver) }.filter { it.good },
                    maxWindKn = daylight.maxOfOrNull { it.windKn } ?: 0.0,
                    maxGustKn = daylight.mapNotNull { it.gustKn }.maxOrNull(),
                )
            }
            .filter { it.hours.isNotEmpty() }

    /** Erster Tag im Alarmzeitraum mit genug gleitbaren Stunden (ab jetzt gerechnet), sonst null. */
    fun alarmDay(
        hours: List<Hour>,
        s: SpotSettings,
        quiver: Quiver = Quiver(),
        now: LocalDateTime = LocalDateTime.now(),
    ): DaySummary? {
        val lastDate = now.toLocalDate().plusDays((s.alarmDays - 1).coerceAtLeast(0).toLong())
        return days(hours, s, quiver, from = now)
            .filter { !it.date.isAfter(lastDate) }
            .firstOrNull { it.goodHours.size >= s.alarmMinHours.coerceAtLeast(1) }
    }

    /** Pro Spot und Gleittag gibt es höchstens einen Alarm. */
    fun signature(day: DaySummary): String = day.date.toString()
}
