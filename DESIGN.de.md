# GitMax – Design

[English](DESIGN.md)

Das gebaute System, nicht der Entwurf: Alle Werte stehen so in `src/main/res/values*` und im Code. Ton laut `PRODUCT.de.md`:
**weich-freundlich**. Modus (Impeccable): **Operate**, Material 3 regelt Struktur und Bedienung, die Marke lebt in Farbrollen,
Schrift, Form und dem Kieselstein.

## Farbe

Strategie **Committed**: ein getönter Grund statt neutralem Grau, ein Akzent, der bewusst von den Statusfarben abweicht.

| Rolle | Hell | Dunkel |
|---|---|---|
| Grund / Fläche | `#F1F9F4` (Salbei) | `#0F1611` (Waldgrau) |
| Text | `#112117` | `#DFE9E2` |
| Akzent `primary` (Beere/Pflaume, Farbton ≈ 330°) | `#8D1767` | `#F19BCC` |
| `primary_container` | `#FDD3EA` | `#661B4B` |
| `secondary` / Container (Salbeigrün) | `#31573F` / `#C2E5CD` | `#A0CAAD` / `#24422F` |
| `tertiary` (Blau) | `#015493` | `#88C0F9` |
| `error` | `#B00C15` | `#FA887D` |
| Git: hinzugefügt (grün) | `#016129` | `#75D78D` |
| Git: geändert (bernstein) | `#734C02` | `#F5B75B` |
| Git: Konflikt (korall) | `#953B01` | `#FF9A6B` |

- Alle Farben sind Material-3-Rollen aus `colors.xml` bzw. `values-night/colors.xml`; in Layouts und Code stehen nur `?attr/…`,
  nie Hex-Werte. Dunkel ist ein eigenes Schema, kein invertiertes Hell.
- Status wird **nie nur über Farbe** getragen: Kieselstein-Glyph, Symbol und Text kommen immer dazu.
- Der Akzent ist absichtlich nicht grün, bernstein, rot oder blau, weil genau diese vier den Status sagen.
- Kontrast: Text auf Fläche mindestens 4,5 : 1, Bedienelemente 3 : 1 (aus `palette.py` berechnet); die Syntaxfarben im
  Viewer erreichen gegen den Grund mindestens 5,3 : 1.
- Systemfarben (Dynamic Color) sind eine Option der Darstellung und standardmäßig aus, damit die Marke bleibt.

## Schrift

- **Figtree** (variabel) für die Oberfläche, **Commit Mono** für Code, Hashes und Pfade; beide SIL OFL 1.1, gebündelt unter
  `res/font/`, Lizenztexte im Über-Bildschirm.
- Material-3-Rollen (`textAppearanceDisplay…Label`), Größen wie Material, Gewichte: Überschriften 700, Titel 600, Text 400,
  Labels 600. Die Familie steckt in jeder Text-Appearance, nicht im Theme (siehe `CLAUDE.md`, „Keine Theme-weite fontFamily“).
- Hashes, Pfade und Zähler stehen in Commit Mono (von Natur aus gleich breit); live veränderliche Zahlen (Fortschritt) nutzen
  `fontFeatureSettings="tnum"`.
- Überschriften und Titel brechen ausgewogen um (`breakStrategy="balanced"`), nie kursiv.

## Form und Raum

- 4-dp-Raster: `space_1…12` = 4, 8, 12, 16, 20, 24, 32, 40, 48 dp. Seitenrand 16 dp; ab 600 dp Breite bleibt der Inhalt auf 640 dp
  begrenzt.
- Radien `xs 8 · s 12 · m 16 · l 24 · xl 28 · full`, **konzentrisch**: äußerer Radius = innerer + Innenabstand. Banner und Karten
  nutzen `m`, die Tonal-Buttons im Repo-Kopf `l`.
- Tonale Erhöhung statt freier Schatten.
- Trefferflächen mindestens 48 dp; dekorative Symbole (24 dp) sind nicht fokussierbar.
- Ab 600 dp wird die untere Leiste zur **Navigationsrail** (96 dp breit, damit „Einstellungen“ nicht abgeschnitten wird); sie steht
  nur auf den vier Hauptzielen, Unterseiten haben einen Zurück-Pfeil.

## Der Kieselstein (Signature)

Jedes Repo ist ein weicher Kieselstein mit Statusring (`PebbleView`): sauber (Häkchen), geändert (Stift), voraus (Pfeil), zurück,
divergiert, Konflikt (gestrichelter Ring, Warnzeichen). Derselbe Ring ist die **Fortschrittsanzeige** bei Klon und Update
(bestimmt oder umlaufend, 1,4 s) und kippt am Ende ins Häkchen. Er erscheint in Liste, Detailkopf, Aktivität und Benachrichtigung.
Erfolg bleibt still: ein Statuswechsel, kein Konfetti.

## Bewegung

Alle Dauern stehen als Tokens in `integers.xml`, Easing in `res/interpolator`, Zugriff über `ui/common/Motion`.

| Token | Wert | Einsatz |
|---|---|---|
| `motion_micro` | 80 ms | Druck-Eingang (Skala 0,96) |
| `motion_quick` | 150 ms | Schließen von Sheets und Dialogen, Loslassen nach Druck |
| `motion_fast` | 250 ms | Öffnen von Sheets und Dialogen, Seitenwechsel, Icon-Wechsel, Kieselstein |
| `smooth_out` | cubic-bezier(0,22 ; 1 ; 0,36 ; 1) | Standard für Oberflächenbewegung |
| `ease_in_out` | cubic-bezier(0,42 ; 0 ; 0,58 ; 1) | Icon- und Textwechsel |

- **Öffnen langsamer als Schließen:** Sheet gleitet in 250 ms herein und in 150 ms hinaus; Dialoge überblenden mit Skala 0,96
  (250 ms auf, 150 ms zu). Seitenwechsel und Icon-Wechsel sind symmetrisch (250 ms).
- Seitenwechsel in die Tiefe: Shared Axis X mit nur **8 dp** Weg (`motion_distance_page`); zwischen den Hauptzielen: Fade-Through.
- Druck-Rückmeldung (`press_scale`, 0,96) nur auf Primärbuttons, FAB und Navigations-Chips, nie auf Listenzeilen.
- Auswahl-Häkchen und Icon-Wechsel: Skalierung 0,25 → 1, Alpha 0 → 1, Unschärfe 4 → 0 dp.
- Hochfrequente Tipps (Vormerken, Zeilenauswahl) haben keine eigene Animation; keine Eingangsanimation beim ersten Zeichnen.
- „Animationen entfernen“ in den Systemeinstellungen wirkt auf alle Animatoren (Dauer 0); der Kieselstein bleibt dann stehen.
- Bewusst nicht umgesetzt: Unschärfe bei Seitenwechseln (Materials Übergänge kennen keine), gestaffelte Eingänge (die App hat
  keine seltenen, großen Eingänge).

## Komponenten und Zustände

Jede bedienbare Komponente kennt Standard, gedrückt, Fokus, deaktiviert, Laden, Fehler, Erfolg und ausgewählt, soweit es für sie Sinn
ergibt. Listen haben Lade-, Leer-, Fehler- und Offline-Zustand mit nächster Handlung (z. B. „Erneut versuchen“). Flüchtiges kommt
als Snackbar (mit Aktion), Dialoge nur bei Entscheidungen, die unterbrechen müssen; zerstörerische Aktionen fragen nach und
sagen, was verloren geht.

- **Banner:** Fehler/Hindernisse in `colorErrorContainer`, Hinweise (z. B. Git LFS) in `colorSecondaryContainer`; Text und Aktion
  stehen untereinander, damit nichts bei großer Schrift mitten im Wort bricht.
- **Aktionszeilen** (Aktualisieren · Committen · Pushen) stehen ab Schriftgröße 1,5 untereinander (`AdaptiveRow`).
- Viewer und Editor: Zeilennummern, Syntaxfärbung aus Theme-Attributen (`colorSyntax…`, hell und dunkel getrennt gewählt).

## Barrierefreiheit

- Schrift bis 2,0 ohne Abschneiden (geprüft auf Lokal, Repo-Detail, Commit-Sheet, Entdecken, Aktivität, Einstellungen).
- Jedes bedienbare Element trägt Text oder Beschreibung; Auswahl und Status über Beschreibung der Zeile, nicht über Farbe.
- Überschriften sind als solche markiert (`accessibilityHeading`), `supportsRtl` ist an. Zurück-Geste und -Taste laufen über den klassischen Weg; Predictive Back
  ist bewusst aus (`enableOnBackInvokedCallback=false`, Begründung im Manifest und in `CLAUDE.md`).
- Zähltexte stehen in Einzahl und Mehrzahl („1 Datei“, „2 Dateien“).
- Sprache: Englisch (Standard) und Deutsch, wählbar in den Einstellungen oder je App in Android.

## Offen / bewusst anders

- Die Impeccable-CLI und die Finish-Reviewer-Agenten sind auf diesem Rechner nicht installiert; die Regeln wurden von Hand
  angewendet und die Abschlussprüfung als frische In-Thread-Prüfung gemacht (siehe Abschlussbericht).
- Emulator-Belege sind kein Hardware-Beleg: Weder Schriftdarstellung noch Bewegung wurden auf einem echten Gerät gesehen.
