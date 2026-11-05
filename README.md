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
- **Segel:** 0,725 × Gewicht ÷ Wind^0,834 (m², Wind in kn), begrenzt auf 2,5–12 m².
  Abgeglichen mit einer üblichen Segeltabelle (80 kg: 10 kn 8,5 m², 12 kn 7,5 m²,
  16 kn 6,0 m², 20 kn 4,7 m², 25 kn 4,0 m²) und dem Praxiswert 108 kg / 15 kn / 8 m².
  Ein- und Aufsteiger: Obergrenze nach surf-Magazin (z. B. 75–90 kg: 5,0–6,2 m²).
- **Board:**
  - Einsteiger: Gewicht + 100 L (Faustformel surf-Magazin)
  - Fortgeschritten (Freeride): Gewicht + Windzuschlag, 12 kn +42 L, 15 kn +27 L,
    20 kn +12 L, 25 kn +2 L, 30 kn −4 L (z. B. 108 kg bei 15 kn → 135 L)
  - Profi: 10 L weniger als Fortgeschritten, Aufsteiger: deutlich mehr (mind. Gewicht + 35 L)
- **Breite:** 38 cm + 0,29 × Volumen. **Finne:** 10 cm + 3,6 × Segel (Waveboard: Segel × 4,4).

Die Logik steckt in `app/src/main/java/de/wuse/windsurfrechner/WindsurfCalculator.kt`.

## Bauen
Jeder Push auf `main` baut per GitHub Actions eine signierte APK
(Artefakt **WindsurfRechner-apk**, außerdem als GitHub-Release `v1.0.<Build>` und im Branch `apk`). Lokal: `./gradlew assembleRelease`.
