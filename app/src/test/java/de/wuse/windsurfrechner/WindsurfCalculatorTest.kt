package de.wuse.windsurfrechner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindsurfCalculatorTest {

    @Test
    fun faustformelFuerFortgeschrittene() {
        val r = WindsurfCalculator.calculate(80.0, 15.0, Level.FORTGESCHRITTEN)
        assertEquals(7.1, r.sailM2, 0.001)
        assertEquals(105, r.volumeL)
        assertEquals("Freerideboard", r.boardType)
    }

    @Test
    fun mehrWindKleineresSegelUndBoard() {
        val schwach = WindsurfCalculator.calculate(75.0, 12.0, Level.FORTGESCHRITTEN)
        val stark = WindsurfCalculator.calculate(75.0, 25.0, Level.FORTGESCHRITTEN)
        assertTrue(stark.sailM2 < schwach.sailM2)
        assertTrue(stark.volumeL < schwach.volumeL)
    }

    @Test
    fun einsteigerBekommenKleinesSegelUndGrossesBoard() {
        val r = WindsurfCalculator.calculate(80.0, 10.0, Level.EINSTEIGER)
        assertEquals(5.7, r.sailM2, 0.001)
        assertEquals(185, r.volumeL)
    }

    @Test
    fun segelBleibtImSinnvollenBereich() {
        assertEquals(12.0, WindsurfCalculator.sailSize(120.0, 6.0, Level.PROFI), 0.001)
        assertEquals(2.5, WindsurfCalculator.sailSize(40.0, 40.0, Level.PROFI), 0.001)
    }

    @Test
    fun boardNieKleinerAlsUntergrenze() {
        val v = WindsurfCalculator.boardVolume(80.0, 40.0, Level.PROFI)
        assertEquals(60, v)
    }

    @Test
    fun beaufortSkala() {
        assertEquals(3, WindsurfCalculator.beaufort(10.0))
        assertEquals(4, WindsurfCalculator.beaufort(11.0))
        assertEquals(4, WindsurfCalculator.beaufort(16.0))
        assertEquals(5, WindsurfCalculator.beaufort(17.0))
        assertEquals(8, WindsurfCalculator.beaufort(34.0))
    }

    @Test
    fun windEinheitenUmrechnen() {
        assertEquals(27.78, WindUnit.KMH.fromKnots(15.0), 0.01)
        assertEquals(15.0, WindUnit.KMH.toKnots(27.78), 0.01)
        assertEquals(15.0, WindUnit.MS.toKnots(WindUnit.MS.fromKnots(15.0)), 0.001)
        assertEquals(4.0, WindUnit.BFT.fromKnots(15.0), 0.001)
        assertEquals(4.0, WindUnit.BFT.fromKnots(WindUnit.BFT.toKnots(4.0)), 0.001)
        assertEquals(15.0, WindUnit.KNOTEN.toKnots(15.0), 0.001)
    }

    @Test
    fun sturmWarnung() {
        val r = WindsurfCalculator.calculate(80.0, 38.0, Level.PROFI)
        assertTrue(r.hints.any { it.isWarning })
    }
}
