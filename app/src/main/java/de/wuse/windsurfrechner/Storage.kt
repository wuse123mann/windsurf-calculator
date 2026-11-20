package de.wuse.windsurfrechner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class Spot(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val region: String = "",
    val lat: Double,
    val lon: Double,
    /** Wind-Alarm für diesen Spot aktiv. */
    val alarm: Boolean = false,
) {
    val covered get() = GeoSphere.covers(lat, lon)
}

/** Alles, was die App speichert – in denselben Einstellungen wie der Rechner. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ----- Fahrer (vom Rechner gesetzt) -----

    fun weight(): Float = prefs.getFloat("gewicht", 80f)
    fun windKnots(): Float = prefs.getFloat("wind", 15f)
    fun level(): Level = enumOr(prefs.getString("stufe", null), Level.FORTGESCHRITTEN)
    fun windUnit(): WindUnit = enumOr(prefs.getString("windeinheit", null), WindUnit.KNOTEN)
    fun sailType(): SailType = enumOr(prefs.getString("segeltyp", null), SailType.OHNE_CAMBER)

    fun saveCalculator(weight: Float, wind: Float, level: Level, unit: WindUnit, sailType: SailType) {
        prefs.edit()
            .putFloat("gewicht", weight)
            .putFloat("wind", wind)
            .putString("stufe", level.name)
            .putString("windeinheit", unit.name)
            .putString("segeltyp", sailType.name)
            .apply()
    }

    // ----- Spots -----

    fun loadSpots(): List<Spot> = runCatching {
        val arr = JSONArray(prefs.getString("spots", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Spot(
                id = o.getString("id"),
                name = o.getString("name"),
                region = o.optString("region", ""),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                alarm = o.optBoolean("alarm", false),
            )
        }
    }.getOrDefault(emptyList())

    fun saveSpots(spots: List<Spot>) {
        val arr = JSONArray()
        spots.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("region", s.region)
                    .put("lat", s.lat)
                    .put("lon", s.lon)
                    .put("alarm", s.alarm),
            )
        }
        prefs.edit().putString("spots", arr.toString()).apply()
    }

    // ----- Vorhersage und Alarm -----

    /** Spot-Einstellungen inkl. der aktuellen Werte aus dem Rechner (Gewicht, Können, Segeltyp). */
    fun loadSpotSettings(): SpotSettings {
        val d = SpotSettings()
        return SpotSettings(
            weightKg = weight().toDouble(),
            level = level(),
            sailType = sailType(),
            minWindKn = prefs.getFloat("minwind", d.minWindKn.toFloat()).toDouble(),
            onlyDaylight = prefs.getBoolean("tagsueber", d.onlyDaylight),
            alarmEnabled = prefs.getBoolean("alarm", d.alarmEnabled),
            alarmDays = prefs.getInt("alarmtage", d.alarmDays).coerceIn(1, 3),
            alarmMinHours = prefs.getInt("alarmstunden", d.alarmMinHours),
        )
    }

    fun saveSpotSettings(s: SpotSettings) {
        prefs.edit()
            .putFloat("minwind", s.minWindKn.toFloat())
            .putBoolean("tagsueber", s.onlyDaylight)
            .putBoolean("alarm", s.alarmEnabled)
            .putInt("alarmtage", s.alarmDays)
            .putInt("alarmstunden", s.alarmMinHours)
            .apply()
    }

    /** Zuletzt gemeldeter Gleittag je Spot – damit derselbe Alarm nicht mehrfach kommt. */
    fun lastAlarm(spotId: String): String? = prefs.getString("alarm_$spotId", null)

    fun setLastAlarm(spotId: String, signature: String) {
        prefs.edit().putString("alarm_$spotId", signature).apply()
    }

    // ----- Eigenes Material -----

    fun loadQuiver(): Quiver = Quiver(
        sails = (prefs.getString("material_segel", "") ?: "").split(';')
            .mapNotNull { it.toDoubleOrNull() }.distinct().sorted(),
        boards = (prefs.getString("material_boards", "") ?: "").split(';')
            .mapNotNull { it.toIntOrNull() }.distinct().sorted(),
    )

    fun saveQuiver(q: Quiver) {
        prefs.edit()
            .putString("material_segel", q.sails.joinToString(";") { String.format(Locale.US, "%.1f", it) })
            .putString("material_boards", q.boards.joinToString(";"))
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(default)

    companion object {
        const val PREFS = "einstellungen"
    }
}
