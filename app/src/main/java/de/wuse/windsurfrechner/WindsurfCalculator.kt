package de.wuse.windsurfrechner

import kotlin.math.min
import kotlin.math.roundToInt

/** Könnerstufe – beeinflusst vor allem das Boardvolumen. */
enum class Level(
    val label: String,
    val description: String,
    /** Liter über dem Körpergewicht bei mittlerem Wind (15 kn). */
    val volumeOffset: Int,
    /** Wie stark der Wind das Volumen verändert (0 = gar nicht, 1 = voll). */
    val windFactor: Double,
    /** Untergrenze: Liter über (oder unter) dem Körpergewicht. */
    val minVolumeOffset: Int,
) {
    EINSTEIGER("Einsteiger", "Lernt gerade, fährt noch nicht im Gleiten", 100, 0.4, 80),
    AUFSTEIGER("Aufsteiger", "Wasserstart, erstes Gleiten im Trapez", 55, 0.8, 30),
    FORTGESCHRITTEN("Fortgeschritten", "Gleitet sicher mit Fußschlaufen und Trapez", 25, 1.0, -5),
    PROFI("Profi", "Powerhalse, Springen, Wellenreiten", 5, 1.0, -20);

    /** Größtes sinnvolles Segel für diese Stufe. */
    fun maxSail(weightKg: Double): Double = when (this) {
        EINSTEIGER -> 2.5 + weightKg * 0.04
        AUFSTEIGER -> 4.0 + weightKg * 0.05
        else -> WindsurfCalculator.MAX_SAIL
    }.coerceAtMost(WindsurfCalculator.MAX_SAIL)
}

/** Einheit für die Windeingabe. Intern rechnet die App immer in Knoten. */
enum class WindUnit(val label: String, val min: Int, val max: Int) {
    KNOTEN("kn", 5, 45),
    KMH("km/h", 10, 83),
    MS("m/s", 3, 23),
    BFT("Bft", 2, 9);

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
        BFT -> BFT_MIDDLE_KNOTS[value.roundToInt().coerceIn(2, 9)] ?: 15.0
    }

    private companion object {
        /** Mitte des Knotenbereichs je Beaufort-Stufe. */
        val BFT_MIDDLE_KNOTS = mapOf(
            2 to 5.0, 3 to 8.5, 4 to 13.5, 5 to 19.0, 6 to 24.5, 7 to 30.5, 8 to 37.0, 9 to 44.0,
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

    /** Bekannte Faustformel: Segelfläche (m²) = Gewicht (kg) × 1,34 ÷ Wind (kn). */
    const val SAIL_FACTOR = 1.34

    private const val MAX_VOLUME = 260.0
    private val TABLE_WINDS = listOf(8, 10, 12, 14, 16, 18, 20, 22, 25, 28, 30, 35)

    private val BEAUFORT_LIMITS = intArrayOf(1, 4, 7, 11, 17, 22, 28, 34, 41, 48, 56, 64)
    private val BEAUFORT_NAMES = listOf(
        "Windstille", "leiser Zug", "leichte Brise", "schwache Brise", "mäßige Brise",
        "frische Brise", "starker Wind", "steifer Wind", "stürmischer Wind", "Sturm",
        "schwerer Sturm", "orkanartiger Sturm", "Orkan",
    )

    fun rawSail(weightKg: Double, windKnots: Double): Double =
        weightKg * SAIL_FACTOR / windKnots.coerceAtLeast(1.0)

    fun sailSize(weightKg: Double, windKnots: Double, level: Level): Double {
        val upper = level.maxSail(weightKg).coerceAtLeast(MIN_SAIL)
        return round1(rawSail(weightKg, windKnots).coerceIn(MIN_SAIL, upper))
    }

    fun boardVolume(weightKg: Double, windKnots: Double, level: Level): Int {
        val volume = weightKg + level.volumeOffset + level.windFactor * (15.0 - windKnots) * 3.0
        val minVolume = weightKg + level.minVolumeOffset
        return roundTo5(volume.coerceIn(minVolume, MAX_VOLUME.coerceAtLeast(minVolume)))
    }

    fun boardType(volumeL: Int, weightKg: Double, level: Level): String {
        if (level == Level.EINSTEIGER) return "Einsteigerboard mit Schwert"
        val extra = volumeL - weightKg
        return when {
            extra >= 60 -> "Großes Freerideboard, gern mit Schwert"
            extra >= 25 -> "Freerideboard"
            extra >= 5 -> "Freemove / Freestyle-Wave"
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

        val width = when (level) {
            Level.EINSTEIGER -> (68 + (volume - 150) * 0.12).coerceIn(70.0, 90.0)
            Level.AUFSTEIGER -> (48 + sail * 3.2).coerceIn(56.0, 88.0)
            else -> (45 + sail * 3.2).coerceIn(54.0, 88.0)
        }.roundToInt()

        val finFactor = if (type == "Waveboard") 4.2 else 5.5
        val fin = (sail * finFactor).coerceIn(16.0, 56.0).roundToInt()

        val bft = beaufort(wind)

        return CalculationResult(
            sailM2 = sail,
            sailMinM2 = round1((sail * 0.92).coerceIn(MIN_SAIL, sailUpper)),
            sailMaxM2 = round1((sail * 1.08).coerceIn(MIN_SAIL, sailUpper)),
            volumeL = volume,
            volumeMinL = (volume - 10).coerceAtLeast(30),
            volumeMaxL = volume + 10,
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

        if (wind < 8) list += Hint(
            "Unter 8 Knoten kommt man kaum ins Gleiten. Mit großem Board, Foil oder einfach " +
                "entspanntem Dahingleiten hast du trotzdem Spaß.", false,
        )
        if (level == Level.EINSTEIGER || level == Level.AUFSTEIGER) {
            if (raw > level.maxSail(weight)) list += Hint(
                "Das Segel ist bewusst kleiner als die Faustformel ergibt – ein großes Segel " +
                    "ist zum Lernen zu schwer und zu kraftvoll.", false,
            )
        } else if (raw > MAX_SAIL) list += Hint(
            "Zum Gleiten bräuchtest du mehr als 12 m². Bei diesem Wind wird es zäh – " +
                "großes Board oder Foil helfen.", true,
        )
        if (raw < MIN_SAIL) list += Hint(
            "Selbst 2,5 m² sind hier eigentlich zu viel. Das ist Wind für Leichtgewichte und Profis.",
            true,
        )
        if (level == Level.EINSTEIGER && wind > 12) list += Hint(
            "Für Einsteiger ist das zu viel Wind. Am besten nur mit Surflehrer oder an einem " +
                "ruhigeren Tag aufs Wasser.", true,
        )
        if (level == Level.AUFSTEIGER && wind > 22) list += Hint(
            "Ganz schön viel Wind für Aufsteiger – nur bei auflandigem Wind und nicht allein.", true,
        )
        if (wind >= 34) list += Hint(
            "Sturm! Nur für sehr erfahrene Surfer, nie allein und nie bei ablandigem Wind.", true,
        )
        if (wind in 8.0..34.0 && list.none { it.isWarning }) list += Hint(
            "Bei böigem Wind lieber eine Nummer kleiner, bei konstantem Wind eher größer wählen.", false,
        )
        return list
    }

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
    private fun roundTo5(value: Double): Int = ((value / 5.0).roundToInt() * 5)
}
