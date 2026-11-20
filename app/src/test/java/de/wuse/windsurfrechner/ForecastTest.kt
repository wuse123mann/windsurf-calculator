package de.wuse.windsurfrechner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ForecastTest {

    private val vienna = ZoneId.of("Europe/Vienna")

    private val json = """
        {"media_type":"application/json","type":"FeatureCollection","version":"v1",
         "timestamps":["2026-11-20T09:00+00:00","2026-11-20T10:00+00:00","2026-11-20T11:00+00:00"],
         "features":[
           {"type":"Feature","geometry":{"type":"Point","coordinates":[16.83,47.85]},
            "properties":{"parameters":{
              "10u":{"name":"u","unit":"m s-1","data":[-5.0,0.0,null]},
              "10v":{"name":"v","unit":"m s-1","data":[0.0,-8.0,3.0]},
              "10fg":{"name":"gust","unit":"m s-1","data":[9.0,12.0,10.0]},
              "2t":{"name":"t","unit":"degC","data":[6.5,7.0,7.5]},
              "tp":{"name":"tp","unit":"kg m-2","data":[1.0,1.4,1.4]},
              "tcc":{"name":"tcc","unit":"1","data":[0.2,0.9,0.5]}}}},
           {"type":"Feature","geometry":{"type":"Point","coordinates":[10.7,45.8]},
            "properties":{"parameters":{
              "10u":{"data":[3.0,3.0,3.0]},
              "10v":{"data":[4.0,4.0,4.0]},
              "10fg":{"data":[null,null,null]},
              "2t":{"data":[10.0,11.0,12.0]},
              "tp":{"data":[0.0,0.0,0.0]},
              "tcc":{"data":[0.0,0.0,0.0]}}}}
         ]}
    """.trimIndent()

    @Test
    fun geosphere_liest_mehrere_Spots_in_Ortszeit() {
        val points = listOf(47.85 to 16.83, 45.8 to 10.7)
        val result = GeoSphere.parse(json, points, vienna)
        assertEquals(2, result.size)

        val a = result[0]
        // dritte Stunde fehlt (u = null) und wird übersprungen
        assertEquals(2, a.size)
        assertEquals(LocalDateTime.of(2026, 11, 20, 10, 0), a[0].time)
        // 5 m/s ≈ 9,7 kn, Wind aus Osten (u negativ = weht nach Westen)
        assertEquals(9.72, a[0].windKn, 0.01)
        assertEquals(90, a[0].dirDeg)
        assertEquals(17.49, a[0].gustKn!!, 0.01)
        // 8 m/s aus Norden
        assertEquals(15.55, a[1].windKn, 0.01)
        assertEquals(0, a[1].dirDeg)
        // Niederschlag: erste Stunde unbekannt, dann Differenz der Summen
        assertNull(a[0].precipMm)
        assertEquals(0.4, a[1].precipMm!!, 0.001)
        assertEquals(20, a[0].cloudPct)
        assertEquals(90, a[1].cloudPct)
        assertEquals(6.5, a[0].tempC!!, 0.001)

        val b = result[1]
        assertEquals(3, b.size)
        assertEquals(9.72, b[0].windKn, 0.01) // 3/4/5-Dreieck → 5 m/s
        assertNull(b[0].gustKn)
        // Wind weht nach Nordosten → kommt aus Südwesten (ca. 217°)
        assertEquals("SW", compass(b[0].dirDeg!!))
    }

    @Test(expected = java.io.IOException::class)
    fun geosphere_falsche_Anzahl_Spots_ist_Fehler() {
        GeoSphere.parse(json, listOf(47.85 to 16.83), vienna)
    }

    @Test
    fun geosphere_url_und_gebiet() {
        val url = GeoSphere.url(listOf(47.85 to 16.83, 45.8 to 10.7))
        assertTrue(url.contains("nwp-v2-1h-1km"))
        assertTrue(url.contains("parameters=10u,10v,10fg,2t,tp,tcc"))
        assertTrue(url.contains("&lat_lon=47.8500,16.8300&lat_lon=45.8000,10.7000"))
        assertTrue(GeoSphere.covers(47.85, 16.83)) // Neusiedler See
        assertTrue(GeoSphere.covers(45.87, 10.86)) // Gardasee
        assertFalse(GeoSphere.covers(36.01, -5.60)) // Tarifa
        assertFalse(GeoSphere.covers(54.43, 11.19)) // Fehmarn
    }

    @Test
    fun sonne_tag_und_nacht_in_wien() {
        val lat = 48.21
        val lon = 16.37
        assertTrue(Sun.elevation(lat, lon, Instant.parse("2026-06-21T11:00:00Z")) > 60)
        // 20. November: Sonnenaufgang ca. 7:10, Untergang ca. 16:15 Ortszeit
        assertFalse(Sun.isDay(lat, lon, Instant.parse("2026-11-20T05:00:00Z")))
        assertTrue(Sun.isDay(lat, lon, Instant.parse("2026-11-20T07:00:00Z")))
        assertTrue(Sun.isDay(lat, lon, Instant.parse("2026-11-20T14:00:00Z")))
        assertFalse(Sun.isDay(lat, lon, Instant.parse("2026-11-20T16:00:00Z")))
    }

    @Test
    fun eigenes_material_naechstes_passendes_teil() {
        val q = Quiver(sails = listOf(5.0, 6.5, 7.8), boards = listOf(110, 135))
        assertEquals(6.5, q.pickSail(7.0)!!, 0.001)
        assertEquals(7.8, q.pickSail(9.0)!!, 0.001)
        assertEquals(5.0, q.pickSail(3.5)!!, 0.001)
        assertEquals(110, q.pickBoard(120))
        assertEquals(135, q.pickBoard(123))
        assertNull(Quiver().pickSail(7.0))
    }

    private fun hour(h: Int, wind: Double, gust: Double? = null, day: Int = 20) = Hour(
        time = LocalDateTime.of(2026, 11, day, h, 0),
        windKn = wind, gustKn = gust, dirDeg = 315, tempC = 10.0, precipMm = 0.0, cloudPct = 20,
        isDay = h in 7..16,
    )

    @Test
    fun praxiswert_108kg_15kn_mit_eigenem_material() {
        val s = SpotSettings(weightKg = 108.0)
        val rec = Advisor.recommend(hour(12, 15.0, 15.0), s, Quiver(sails = listOf(6.5, 7.8), boards = listOf(135)))
        assertEquals(8.2, rec.sailM2, 0.001)
        assertEquals(7.8, rec.shownSail, 0.001)
        assertEquals(135, rec.shownBoard)
        assertTrue(rec.good)
    }

    @Test
    fun alarm_findet_ersten_Tag_mit_genug_Gleitstunden() {
        val hours = (0..23).map { hour(it, if (it in 12..13) 16.0 else 6.0, day = 20) } +
            (0..23).map { hour(it, if (it in 10..15) 18.0 else 6.0, day = 21) }
        val now = LocalDateTime.of(2026, 11, 20, 8, 0)

        assertEquals(20, Advisor.alarmDay(hours, SpotSettings(alarmDays = 2, alarmMinHours = 2), now = now)!!.date.dayOfMonth)

        val day = Advisor.alarmDay(hours, SpotSettings(alarmDays = 2, alarmMinHours = 3), now = now)
        assertNotNull(day)
        assertEquals(21, day!!.date.dayOfMonth)
        assertEquals(10, day.firstGood)
        assertEquals(15, day.lastGood)

        assertNull(Advisor.alarmDay(hours, SpotSettings(alarmDays = 1, alarmMinHours = 3), now = now))
    }

    @Test
    fun koordinaten_eingabe() {
        val a = Places.parseCoordinates("47.855, 16.830")!!
        assertEquals(47.855, a.lat, 1e-9)
        assertEquals(16.830, a.lon, 1e-9)
        val b = Places.parseCoordinates("47,85 16,83")!!
        assertEquals(47.85, b.lat, 1e-9)
        assertEquals(16.83, b.lon, 1e-9)
        assertNull(Places.parseCoordinates("Podersdorf"))
        assertNull(Places.parseCoordinates("95, 16"))
    }
}
