# Windsurf-Rechner 🏄

Android-App, die aus **Körpergewicht (kg)** und **Wind** (wahlweise in Knoten, km/h, m/s
oder Beaufort) die passende
**Segelgröße** und **Boardgröße** berechnet.

## Was die App anzeigt
- Segelgröße in m² (mit sinnvollem Bereich)
- Boardvolumen in Litern, Boardtyp, ungefähre Breite und Finnenlänge
- Wind in km/h, m/s und Beaufort
- Hinweise und Warnungen (zu wenig Wind, zu viel Wind für die Könnerstufe, Sturm)
- Tabelle: welches Segel/Board bei welchem Wind für dein Gewicht
- **Umrechner** (eigener Tab): Knoten ⇄ km/h ⇄ m/s ⇄ Beaufort (+ mph), mit Beaufort-Skala
  und Übernahme des Werts in den Rechner

Zusätzlich lässt sich die Könnerstufe wählen (Einsteiger bis Profi), weil sie
vor allem das Boardvolumen stark beeinflusst.

## Formeln (Richtwerte)
- **Segel:** 0,874 × Gewicht ÷ Wind^0,822 (m², Wind in kn), begrenzt auf 2,5–12 m².
  Abgeglichen mit üblichen Segeltabellen (75 kg: 12 kn ≈ 8,5 m², 15 kn ≈ 7,0 m²,
  22 kn ≈ 5,2 m², 30 kn ≈ 4,0 m²). Die bekannte Faustformel Gewicht × 1,34 ÷ Knoten
  liefert bei viel Wind zu kleine Segel.
- **Board:** Gleitvolumen eines guten Surfers = 25 L + Gewicht × Faktor(Wind)
  (Faktor 1,33 bei 12 kn, 1,11 bei 15 kn, 0,90 bei 20 kn, 0,72 bei 25 kn, 0,63 bei 30 kn).
  Fortgeschritten: × 1,10 + 5 L, Aufsteiger: × 1,25 + 15 L, Einsteiger: Gewicht + 110 L.
  Beispiel 90 kg bei 15 kn: Profi 125 L, Fortgeschritten 140 L.
- **Breite:** 38 cm + 0,29 × Volumen. **Finne:** Segel × 5,2 cm (Waveboard × 4,4).

Die Logik steckt in `app/src/main/java/de/wuse/windsurfrechner/WindsurfCalculator.kt`.

## Bauen
Jeder Push auf `main` baut per GitHub Actions eine signierte APK
(Artefakt **WindsurfRechner-apk**, außerdem immer die neueste APK im Branch `apk`). Lokal: `./gradlew assembleRelease`.
