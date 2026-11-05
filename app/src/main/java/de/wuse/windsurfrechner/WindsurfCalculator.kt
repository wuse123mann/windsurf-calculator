package de.wuse.windsurfrechner

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Könnerstufe – beeinflusst vor allem das Boardvolumen.
 * Grundlage ist das Gleitvolumen eines sehr guten Surfers; je weniger Erfahrung,
 * desto mehr Volumen (Stabilität, Angleiten, Wasserstart) kommt dazu.
 */
enum class Level(
    val label: String,
    val description: String,
    val volumeFactor: Double,
    val volumeAdd: Double,
) {
    EINSTEIGER("Einsteiger", "Lernt gerade, fährt noch nicht im Gleiten", 1.0, 0.0),
    AUFSTEIGER("Aufsteiger", "Wasserstart, erstes Gleiten im Trapez", 1.25, 15.0),
    FORTGESCHRITTEN("Fortgeschritten", "Gleitet sicher mit Fußschlaufen und Trapez", 1.10, 5.0),
    PROFI("Profi", "Powerhalse, Springen, Wellenreiten", 1.0, 0.0);

    /** Größtes sinnvolles Segel für diese Stufe. */
    fun maxSail(weightKg: Double): Double = when (this) {
        EINSTEIGER -> 2.5 + weightKg * 0.04
        AUFSTEIGER -> 4.0 + weightKg * 0.05
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
     * Segelfläche (m²) = 0,874 × Gewicht ÷ Wind^0,822.
     * Angepasst an übliche Segel-Wind-Tabellen (z. B. 75 kg: 12 kn ≈ 8,5 m²,
     * 15 kn ≈ 7,0 m², 22 kn ≈ 5,2 m², 30 kn ≈ 4,0 m²). Die einfache Faustformel
     * Gewicht × 1,34 ÷ Knoten liefert bei viel Wind zu kleine Segel.
     */
    private const val SAIL_K = 0.874
    private const val SAIL_EXP = 0.822

    private const val MAX_VOLUME = 250.0

    /**
     * Gleitvolumen eines guten Surfers: 25 L + Gewicht × Faktor(Wind).
     * Beispiele: 90 kg → 15 kn ≈ 125 L, 20 kn ≈ 105 L, 25 kn ≈ 90 L, 30 kn ≈ 80 L.
     */
    private const val VOLUME_BASE = 25.0
    private val VOLUME_FACTOR = listOf(
        5.0 to 1.85, 8.0 to 1.75, 10.0 to 1.55, 12.0 to 1.33, 15.0 to 1.11, 18.0 to 0.97,
        20.0 to 0.90, 22.0 to 0.82, 25.0 to 0.72, 30.0 to 0.63, 35.0 to 0.58, 45.0 to 0.55,
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

    private fun volumeFactor(windKnots: Double): Double {
        val w = windKnots.coerceIn(VOLUME_FACTOR.first().first, VOLUME_FACTOR.last().first)
        for (i in 0 until VOLUME_FACTOR.lastIndex) {
            val (a, fa) = VOLUME_FACTOR[i]
            val (b, fb) = VOLUME_FACTOR[i + 1]
            if (w <= b) return fa + (fb - fa) * (w - a) / (b - a)
        }
        return VOLUME_FACTOR.last().second
    }

    /** Volumen, mit dem ein sehr guter Surfer bei diesem Wind sicher gleitet. */
    fun planingVolume(weightKg: Double, windKnots: Double): Double =
        VOLUME_BASE + weightKg * volumeFactor(windKnots)

    fun boardVolume(weightKg: Double, windKnots: Double, level: Level): Int {
        val volume = when (level) {
            // Einsteiger brauchen vor allem Stabilität – fast unabhängig vom Wind.
            Level.EINSTEIGER -> weightKg + 110 + (12.0 - windKnots).coerceAtLeast(0.0) * 2
            Level.AUFSTEIGER -> (planingVolume(weightKg, windKnots) * level.volumeFactor + level.volumeAdd)
                .coerceAtLeast(weightKg + 50)
            else -> planingVolume(weightKg, windKnots) * level.volumeFactor + level.volumeAdd
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
        val finFactor = if (type == "Waveboard") 4.4 else 5.2
        val fin = (sail * finFactor).coerceIn(16.0, 56.0).roundToInt()

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
