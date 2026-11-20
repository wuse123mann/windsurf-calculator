# Windsurf-Rechner 🏄

Android-App, die aus **Körpergewicht (kg)** und **Wind** (wahlweise in Knoten, km/h, m/s
oder Beaufort) die passende **Segelgröße** und **Boardgröße** berechnet – plus Spots mit
stündlicher Vorhersage, Wind-Alarm und eigenem Material.

## Gratis
- **Rechner:** Segelgröße in m² (mit Bereich), Boardvolumen, Boardtyp, Breite, Finne,
  Hinweise und Tabelle „welches Segel/Board bei welchem Wind“; Segeltyp (mit/ohne Camber)
  und Könnerstufe wählbar
- **Umrechner:** Knoten ⇄ km/h ⇄ m/s ⇄ Beaufort (+ mph), mit Beaufort-Skala

## Premium (Einmalkauf, kein Abo)
- **Spots:** per Ortssuche oder Koordinaten anlegen; pro Spot stündlich Wind, Böen, Richtung,
  Temperatur, Regen und die passende Segel- und Boardgröße für die nächsten ca. 2½ Tage
- **Wind-Alarm 🔔:** prüft ca. alle 3 Stunden im Hintergrund und meldet sich, wenn an einem Spot
  genug gleitbare Stunden angesagt sind (heute / heute + morgen / bis übermorgen, höchstens
  einmal pro Spot und Tag)
- **Material:** eigene Segel und Boards eintragen – Rechner und Vorhersage zeigen dann,
  welches eigene Teil am besten passt

## Varianten
| Variante | Wofür | Premium |
|---|---|---|
| `github` | APK im GitHub-Release (eigene Nutzung) | immer frei |
| `play` | App-Bundle für den Play Store | Einmalkauf über Google Play Billing, Produkt-ID `premium` |

## Wetterdaten
Die Vorhersage kommt von **GeoSphere Austria** (staatlicher Wetterdienst), Modell
C-LAEF AlpeAdria mit 1 km Auflösung, stündlich, ca. 60 Stunden voraus
(`nwp-v2-1h-1km` über die [Dataset-API](https://dataset.api.hub.geosphere.at/v1/docs/)).
Die Daten stehen unter **CC BY 4.0** – kostenlos, ohne Konto und Schlüssel, und dürfen mit
Quellenangabe auch in einer Bezahl-App genutzt werden. Abgedeckt ist der erweiterte Alpenraum
(ca. 43–51,5° N, 5–22,5° O: Österreich, Schweiz, Süddeutschland, Norditalien inkl. Gardasee,
Slowenien, Istrien, Ungarn inkl. Balaton). Die Ortssuche nutzt den Geocoder von Android.

## Formeln (Richtwerte)
- **Segel:** 0,725 × Gewicht ÷ Wind^0,834 (m², Wind in kn), begrenzt auf 2,5–12 m².
  Abgeglichen mit einer üblichen Segeltabelle (80 kg: 10 kn 8,5 m², 12 kn 7,5 m²,
  16 kn 6,0 m², 20 kn 4,7 m², 25 kn 4,0 m²) und dem Praxiswert 108 kg / 15 kn / 8 m².
  Ein- und Aufsteiger: Obergrenze nach surf-Magazin (z. B. 75–90 kg: 5,0–6,2 m²).
- **Camber-Segel:** 5 % kleiner als ohne Camber bei gleicher Zugkraft
  (z. B. 8,2 m² ohne Camber ≈ 7,8 m² mit Camber); Finne richtet sich nach der Zugkraft.
- **Board:**
  - Einsteiger: Gewicht + 100 L (Faustformel surf-Magazin)
  - Fortgeschritten (Freeride): Gewicht + Windzuschlag, 12 kn +42 L, 15 kn +27 L,
    20 kn +12 L, 25 kn +2 L, 30 kn −4 L (z. B. 108 kg bei 15 kn → 135 L)
  - Profi: 10 L weniger als Fortgeschritten, Aufsteiger: deutlich mehr (mind. Gewicht + 35 L)
- **Breite:** 38 cm + 0,29 × Volumen. **Finne:** 10 cm + 3,6 × Segel (Waveboard: Segel × 4,4).

Die Logik steckt in `app/src/main/java/de/wuse/windsurfrechner/WindsurfCalculator.kt`.

Die Vorhersage-Logik (GeoSphere, Sonnenstand, Segel pro Stunde, Alarm) steckt in
`app/src/main/java/de/wuse/windsurfrechner/Forecast.kt`.

## Bauen
Jeder Push auf `main` baut per GitHub Actions:
- die signierte APK der `github`-Variante (Artefakt **WindsurfRechner-apk**, GitHub-Release
  `v1.0.<Build>` und Branch `apk`)
- das App-Bundle der `play`-Variante (Artefakt **WindsurfRechner-play-aab**) für die Play Console

Lokal: `./gradlew assembleGithubRelease` bzw. `./gradlew bundlePlayRelease`.
