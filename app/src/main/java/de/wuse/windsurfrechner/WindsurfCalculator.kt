package de.wuse.windsurfrechner

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Könnerstufe – beeinflusst vor allem das Boardvolumen.
 * Grundlage ist das Freeride-Volumen eines sicher gleitenden Surfers (Fortgeschritten).
 */
enum class Level(val label: String, val description: String) {
    EINSTEIGER("Einsteiger", "Lernt gerade, fährt noch nicht im Gleiten"),
    AUFSTEIGER("Aufsteiger", "Wasserstart, erstes Gleiten im Trapez"),
    FORTGESCHRITTEN("Fortgeschritten", "Gleitet sicher mit Fußschlaufen und Trapez"),
    PROFI("Profi", "Powerhalse, Springen, Wellenreiten");

    /**
     * Größtes sinnvolles Segel. Für Ein- und Aufsteiger nach surf-Magazin:
     * < 60 kg 4,0–4,2 m², 60–75 kg 4,2–5,3 m², 75–90 kg 5,0–6,2 m², > 90 kg 5,5–6,5 m².
     */
    fun maxSail(weightKg: Double): Double = when (this) {
        EINSTEIGER -> (1.0 + weightKg * 0.05).coerceAtMost(6.0)
        AUFSTEIGER -> (1.3 + weightKg * 0.055).coerceAtMost(6.5)
        else -> WindsurfCalculator.MAX_SAIL
    }.coerceAtMost(WindsurfCalculator.MAX_SAIL)
}

/** Einheit für die Windeingabe. Intern rechnet die App immer in Knoten. */
enum class WindUnit(val label: String, val min: Int, val max: Int, val converterMax: Double) {
    KNOTEN("kn", 5, 45, 80.0),
    KMH("km/h", 10, 83, 150.0),
    MS("m/s", 3, 23, 41.0),
    BFT("Bft", 2, 9, 12.0);

    fun fromKnots(knots: Double): Double = when (this) {
        KNOTEN -> knots
        KMH -> knots * 1.852
        MS -> knots * 0.5144
        BFT -> WindsurfCalculator.beaufort(knots).toDouble()
    }

    fun toKnots(value: Double): Double = when (this) {
        KNOTEN -> value
        KMH -> value / 1.852
        MS -> value / 0.5144
        BFT -> BFT_MIDDLE_KNOTS[value.roundToInt().coerceIn(0, 12)] ?: 15.0
    }

    private companion object {
        /** Mitte des Knotenbereichs je Beaufort-Stufe. */
        val BFT_MIDDLE_KNOTS = mapOf(
            0 to 0.5, 1 to 2.0, 2 to 5.0, 3 to 8.5, 4 to 13.5, 5 to 19.0, 6 to 24.5,
            7 to 30.5, 8 to 37.0, 9 to 44.0, 10 to 51.5, 11 to 59.5, 12 to 66.0,
        )
    }
}

data class Hint(val text: String, val isWarning: Boolean)

data class TableRow(val windKnots: Int, val sailM2: Double, val volumeL: Int)

data class CalculationResult(
    val sailM2: Double,
    val sailMinM2: Double,
    val sailMaxM2: Double,
    val volumeL: Int,
    val volumeMinL: Int,
    val volumeMaxL: Int,
    val boardWidthCm: Int,
    val finCm: Int,
    val boardType: String,
    val beaufort: Int,
    val beaufortName: String,
    val windKmh: Double,
    val windMs: Double,
    val hints: List<Hint>,
)

object WindsurfCalculator {
    const val MIN_WEIGHT = 30.0
    const val MAX_WEIGHT = 150.0
    const val MIN_WIND = 5.0
    const val MAX_WIND = 45.0
    const val MIN_SAIL = 2.5
    const val MAX_SAIL = 12.0

    /**
     * Segelfläche (m²) = 0,725 × Gewicht ÷ Wind^0,834 (Wind in Knoten).
     * Angepasst an übliche Segeltabellen, z. B. 80 kg: 10 kn 8,5 m², 12 kn 7,5 m²,
     * 16 kn 6,0 m², 20 kn 4,7 m², 25 kn 4,0 m² – und an den Praxiswert
     * 108 kg bei 15 kn mit 8 m².
     */
    private const val SAIL_K = 0.725
    private const val SAIL_EXP = 0.834

    private const val MAX_VOLUME = 250.0

    /**
     * Liter über dem Körpergewicht für ein Freeride-Board (Fortgeschritten), je nach Wind.
     * 15 kn: Gewicht + 27 L (z. B. 108 kg → 135 L, 80 kg → 105 L), 25 kn: etwa Gewicht.
     */
    private val FREERIDE_EXTRA = listOf(
        5.0 to 85.0, 8.0 to 70.0, 10.0 to 55.0, 12.0 to 42.0, 15.0 to 27.0, 18.0 to 17.0,
        20.0 to 12.0, 22.0 to 7.0, 25.0 to 2.0, 30.0 to -4.0, 35.0 to -8.0, 45.0 to -12.0,
    )

    private val TABLE_WINDS = listOf(8, 10, 12, 14, 16, 18, 20, 22, 25, 28, 30, 35)

    private val BEAUFORT_LIMITS = intArrayOf(1, 4, 7, 11, 17, 22, 28, 34, 41, 48, 56, 64)
    private val BEAUFORT_NAMES = listOf(
        "Windstille", "leiser Zug", "leichte Brise", "schwache Brise", "mäßige Brise",
        "frische Brise", "starker Wind", "steifer Wind", "stürmischer Wind", "Sturm",
        "schwerer Sturm", "orkanartiger Sturm", "Orkan",
    )

    fun rawSail(weightKg: Double, windKnots: Double): Double =
        SAIL_K * weightKg / windKnots.coerceAtLeast(1.0).pow(SAIL_EXP)

    fun sailSize(weightKg: Double, windKnots: Double, level: Level): Double {
        val upper = level.maxSail(weightKg).coerceAtLeast(MIN_SAIL)
        return round1(rawSail(weightKg, windKnots).coerceIn(MIN_SAIL, upper))
    }

    private fun freerideExtra(windKnots: Double): Double {
        val w = windKnots.coerceIn(FREERIDE_EXTRA.first().first, FREERIDE_EXTRA.last().first)
        for (i in 0 until FREERIDE_EXTRA.lastIndex) {
            val (a, fa) = FREERIDE_EXTRA[i]
            val (b, fb) = FREERIDE_EXTRA[i + 1]
            if (w <= b) return fa + (fb - fa) * (w - a) / (b - a)
        }
        return FREERIDE_EXTRA.last().second
    }

    fun boardVolume(weightKg: Double, windKnots: Double, level: Level): Int {
        val extra = freerideExtra(windKnots)
        val volume = when (level) {
            // surf-Magazin-Faustformel für Einsteiger: Gewicht + 100 L
            Level.EINSTEIGER -> weightKg + 100
            Level.AUFSTEIGER -> weightKg + (0.6 * extra + 40).coerceAtLeast(35.0)
            Level.FORTGESCHRITTEN -> (weightKg + extra).coerceAtLeast(maxOf(weightKg - 3, 60.0))
            Level.PROFI -> (weightKg + extra - 10).coerceAtLeast(maxOf(weightKg - 10, 60.0))
        }
        return roundTo5(volume.coerceIn(50.0, MAX_VOLUME))
    }

    fun boardType(volumeL: Int, weightKg: Double, level: Level): String {
        if (level == Level.EINSTEIGER) return "Einsteigerboard mit Schwert"
        val ratio = volumeL / weightKg
        return when {
            ratio >= 1.6 -> if (level == Level.AUFSTEIGER) "Großes Freerideboard mit Schwert" else "Großes Freeride- / Formulaboard"
            ratio >= 1.25 -> if (level == Level.PROFI) "Freeride / Slalom" else "Freerideboard"
            ratio >= 1.05 -> "Freemove / Freestyle"
            ratio >= 0.95 -> "Freestyle-Wave / Bump & Jump"
            else -> "Waveboard"
        }
    }

    fun beaufort(windKnots: Double): Int = BEAUFORT_LIMITS.count { windKnots >= it }

    fun beaufortName(bft: Int): String = BEAUFORT_NAMES[bft.coerceIn(0, BEAUFORT_NAMES.lastIndex)]

    fun calculate(weightKg: Double, windKnots: Double, level: Level): CalculationResult {
        val weight = weightKg.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        val wind = windKnots.coerceIn(MIN_WIND, MAX_WIND)

        val sail = sailSize(weight, wind, level)
        val sailUpper = min(MAX_SAIL, level.maxSail(weight)).coerceAtLeast(MIN_SAIL)
        val volume = boardVolume(weight, wind, level)
        val type = boardType(volume, weight, level)

        val width = (38 + volume * 0.29).coerceIn(54.0, 92.0).roundToInt()
        // Freeride-Finne ≈ 10 cm + 3,6 × Segel (7,2–8,6 m² → 36–40 cm), Wavefinne kürzer
        val finRaw = if (type == "Waveboard") sail * 4.4 else 10 + sail * 3.6
        val fin = finRaw.coerceIn(16.0, 52.0).roundToInt()

        val bft = beaufort(wind)

        return CalculationResult(
            sailM2 = sail,
            sailMinM2 = round1((sail * 0.93).coerceIn(MIN_SAIL, sailUpper)),
            sailMaxM2 = round1((sail * 1.07).coerceIn(MIN_SAIL, sailUpper)),
            volumeL = volume,
            volumeMinL = roundTo5(volume * 0.92),
            volumeMaxL = roundTo5((volume * 1.08).coerceAtMost(MAX_VOLUME)),
            boardWidthCm = width,
            finCm = fin,
            boardType = type,
            beaufort = bft,
            beaufortName = beaufortName(bft),
            windKmh = wind * 1.852,
            windMs = wind * 0.5144,
            hints = hints(weight, wind, level),
        )
    }

    fun table(weightKg: Double, level: Level): List<TableRow> = TABLE_WINDS.map { kn ->
        TableRow(kn, sailSize(weightKg, kn.toDouble(), level), boardVolume(weightKg, kn.toDouble(), level))
    }

    private fun hints(weight: Double, wind: Double, level: Level): List<Hint> {
        val list = mutableListOf<Hint>()
        val raw = rawSail(weight, wind)

        if (wind < 10 && level != Level.EINSTEIGER) list += Hint(
            "Unter etwa 10 Knoten kommt man kaum ins Gleiten. Dafür braucht es ein sehr großes " +
                "Board mit großem Segel – oder ein Foil.", false,
        )
        if (level == Level.EINSTEIGER || level == Level.AUFSTEIGER) {
            if (raw > level.maxSail(weight)) list += Hint(
                "Das Segel ist bewusst kleiner gewählt, als es zum Gleiten nötig wäre – ein großes " +
                    "Segel ist zum Lernen zu schwer und zu kraftvoll.", false,
            )
        } else if (raw > MAX_SAIL) list += Hint(
            "Zum Gleiten bräuchtest du mehr als 12 m². Bei diesem Wind wird es zäh – " +
                "Formulaboard oder Foil helfen.", true,
        )
        if (raw < MIN_SAIL) list += Hint(
            "Selbst 2,5 m² sind hier eigentlich zu viel. Das ist Wind für Leichtgewichte und Profis.",
            true,
        )
        if (level == Level.EINSTEIGER && wind > 12) list += Hint(
            "Für Einsteiger ist das zu viel Wind. Ideal zum Lernen sind 5–12 Knoten – am besten " +
                "mit Surflehrer oder an einem ruhigeren Tag.", true,
        )
        if (level == Level.AUFSTEIGER && wind > 22) list += Hint(
            "Ganz schön viel Wind für Aufsteiger – nur bei auflandigem Wind und nicht allein.", true,
        )
        if (wind >= 34) list += Hint(
            "Sturm! Nur für sehr erfahrene Surfer, nie allein und nie bei ablandigem Wind.", true,
        )
        if (list.none { it.isWarning }) list += Hint(
            "Bei böigem Wind lieber eine Nummer kleiner, bei konstantem Wind eher größer wählen.", false,
        )
        return list
    }

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
    private fun roundTo5(value: Double): Int = ((value / 5.0).roundToInt() * 5)
}
