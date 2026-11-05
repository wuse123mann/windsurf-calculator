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
- **Segel:** Gewicht × 1,34 ÷ Knoten, begrenzt auf 2,5–12 m²
  (für Einsteiger/Aufsteiger zusätzlich nach oben begrenzt)
- **Board:** Gewicht + Aufschlag je Stufe (Einsteiger +100 L … Profi +5 L),
  plus 3 L je Knoten unter 15 kn bzw. minus 3 L je Knoten darüber
- **Finne:** Segelfläche × 5,5 cm (Waveboard × 4,2)

Die Logik steckt in `app/src/main/java/de/wuse/windsurfrechner/WindsurfCalculator.kt`.

## Bauen
Jeder Push auf `main` baut per GitHub Actions eine signierte APK
(Artefakt **WindsurfRechner-apk**). Lokal: `./gradlew assembleRelease`.
