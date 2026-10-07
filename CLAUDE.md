# CLAUDE.md

> Developer notes for Claude Code, written in German like the source comments. The README (English, [German](README.de.md))
> is the entry point for everyone else.

Leitfaden für Claude Code in diesem Repository.

## Projekt

**GitMax** ist eine native Android-App (Java, Package `de.lembergmax.gitmax`) als Git-Client zunächst nur für
Max: GitHub und GitLab verknüpfen, Repos einzeln oder gebündelt in einen Ordner auf dem Handy klonen
und aktualisieren, committen und pushen (Standardfunktionen); alle weiteren Git-Funktionen im Bereich
„Erweitert“. Oberfläche Englisch (Standard) und Deutsch. Quellcode auf GitHub, Installation per Sideload, kein Play Store.

Der vollständige Plan (Meilensteine M0–M11) lag in der Planungsrunde außerhalb des Repos. Diese Datei hält nur fest, was beim
Arbeiten im Code gilt.

## Stack (wie die Schwesterprojekte, mit einer Abweichung)

- **Java 21**, Single-Modul (Plugin auf dem Wurzelprojekt, Quellen unter `src/`, Aufgaben unpräfixiert),
  AGP 8.13.1, Gradle 8.14.5, XML-Views + Material 3, **kein Kotlin, keine Compose**.
- **`minSdk = 34`** (Schwesterprojekte: 29), `targetSdk`/`compileSdk = 36`. Grund: JGit 6/7 nutzt Java-API,
  die erst mit Android 14 sicher vorhanden ist (`InputStream.readNBytes` fehlt unter API 33).
- Git-Engine **JGit 7.8** (`org.eclipse.jgit`), SSH über `org.eclipse.jgit.ssh.apache` (Apache MINA SSHD).
- Records sind erlaubt (minSdk 34).
- Bezeichner englisch, Kommentare und Javadoc deutsch.
- **Oberflächentexte** stehen in `res/values/strings.xml` (Englisch, Standard) **und** `res/values-de/strings.xml` (Deutsch), Zähltexte in
  `plurals.xml` beider Ordner. Jeder neue Text kommt in beide; ein Skript-Abgleich der Schlüssel und Platzhalter hat sich bewährt.
  `androidResources.localeFilters` lässt nur `en` und `de` zu, `res/xml/locales_config.xml` meldet sie dem System. Die Sprache wählt der Nutzer
  in den Einstellungen (`ui/common/Languages`, `AppCompatDelegate.setApplicationLocales`) oder Android je App.
- **Technische Meldungen** (Ausnahmetexte, Standard-Commit-Nachrichten wie „Initial commit“, „Merge“, „Stashed changes“) sind Englisch und
  gehen nie durch `strings.xml`; die Oberfläche zeigt je Fehlerart einen übersetzten Text und die technische Meldung nur im Protokoll.

## Build / Test

`gradlew.bat` auf Windows, `./gradlew` sonst (JDK 21, Android SDK; Pfad in `local.properties`).

```
./gradlew.bat testDebugUnitTest                                    # JVM-Tests (JUnit 4)
./gradlew.bat assembleDebug                                        # Debug-APK
./gradlew.bat assembleRelease                                      # R8-geschrumpft (unsigniert ohne Keystore)
./gradlew.bat connectedDebugAndroidTest                            # Instrumentierungstests auf Emulator/Gerät
./gradlew.bat -PtestBuildType=minified connectedMinifiedAndroidTest # dieselben Tests gegen die R8-App
```

Der Build-Typ **`minified`** ist wie `release`, aber debug-signiert und bewusst *nicht* debuggbar (sonst
schaltet R8 Optimierung und Obfuskation ab). Er existiert, weil JGit per Reflection arbeitet und R8 es
brechen kann. `proguard-test-rules.pro` gilt nur dafür und hält App- und Test-Runner-Klassen.

Emulatoren: `KCD_Pixel` (API 35) und `GitMax_API36` (API 36.1). Der API-34-Systemabbild-Ordner ist leer
(nicht installiert).

## Erkenntnisse aus dem Technik-Spike (M0, API 35)

Gemessen mit `src/androidTest/.../spike/JgitSpikeTest.java` auf dem Telefonspeicher
(`/storage/emulated/0`, FUSE):

- Klonen, Commit, Push, Pull über `file://` funktionieren. Der Klon läuft als
  `setNoCheckout(true)` → `RepoConfigurator.applySharedStorageFlags` → `reset --hard HEAD`.
- **Symlinks und Hardlinks sind verboten** (`AccessDeniedException`). Mit `core.symlinks=false` wird ein
  Symlink-Eintrag als Datei mit dem Linkziel ausgecheckt, der Status danach ist sauber.
- **Dateinamen mit `: ? * " < > | \` sind auf dem Telefonspeicher verboten** (`Operation not permitted`).
  Umlaute, Emoji, Leerzeichen, Großbuchstaben, Punkt am Ende sind erlaubt. Repos mit solchen Namen
  brauchen einen toleranten Checkout (siehe Plan, M4).
- `FS.supportsSymlinks()` meldet trotzdem `true` (nur Heuristik), deshalb ist `core.symlinks=false`
  zwingend. `createNewFileAtomic` klappt trotz fehlender Hardlinks, `core.supportsAtomicCreateNewFile`
  muss **nicht** gesetzt werden.
- SSH: Der Handshake gegen github.com läuft bis zur Authentifizierung (`publickey: no keys to try`).

- **Auto-GC ist abgeschaltet** (`AppSystemReader`: `gc.auto=0`): JGits `GC$PidLock` nutzt
  `java.lang.ProcessHandle`, das es auf Android nicht gibt. Ein späteres „Aufräumen“ darf nicht
  `Git.gc()` aufrufen, sondern `GC.packRefs()`, `repack()` und `prune()` einzeln.
- **R8-Spike bestanden** (Build-Typ `minified`, JGit obfuskiert): Klon, Commit, Push, Pull und der
  SSH-Handshake laufen. Dafür mussten die Test-Keep-Regeln Kotlin und `androidx.tracing` halten.

### Go/No-Go M0 (2026-10-03): **Go**

- Verifiziert: API 35 (Emulator `KCD_Pixel`), Debug und R8. **Nicht** auf einem echten API-34-Abbild
  getestet (Abbild nicht installiert, 1,5 GB Download) und API 36.1 (Emulator `GitMax_API36` blieb beim
  ersten Start hängen). Als Ersatz prüft eine statische Analyse alle `java.*`-/`javax.*`-Aufrufe von
  JGit, MINA SSHD, JavaEWAH und SLF4J gegen die `android.jar` von API 34/35/36: Ergebnis überall
  gleich, nichts im genutzten Pfad fehlt (offen nur: JMX-Monitoring, PKCS#11, JAAS/GSS, `ProcessHandle`
  im GC, `ByteBuffer`-Kovarianten, die D8 behandelt).
- Offen für M11: echter API-34-Lauf und API-36.1-Lauf, falls gewünscht (braucht Download-Freigabe).

## Stand der Meilensteine

- **M0** Technik-Spike: Go (siehe oben).
- **M1** Design-System und App-Hülle: Tokens (`colors.xml` aus `palette.py` berechnet und auf Kontrast
  geprüft), Figtree + Commit Mono, Material-Icons als Vektoren, `PebbleView` (Kieselstein-Status),
  Navigation (eine Activity, Leiste kompakt / Rail ab 600 dp), Debug-Galerie
  (`GalleryActivity`, nur Debug).
- **M2** Konten und Anbieter-APIs: `SecretVault` (Keystore AES-GCM), `AccountRepository`,
  `GithubClient`/`GitlabClient`, Screens Konten/Verbinden/Detail.
- **M3** Arbeitsordner, Ordnerauswahl, `RepoScanner`, `RepoStatusReader`, Lokal-Liste (auf dem Emulator geprüft).
- **M4** Git-Engine und Vorgänge: `JgitEngine` (Klon, Update mit Merge/Rebase/FF-only und Auto-Stash, Fetch,
  Status, Stage, Commit, Push, Abbruch), `GitErrorMapper`, `AccountCredentials`, `OperationQueue`
  (ein Klon, zwei andere zugleich, je Ordner einer), `OperationHistory` (letzte 50), `GitOperationService`
  (Vordergrund, `dataSync`, Benachrichtigungen, WakeLock, `onTimeout`), `PersistentCloneJournal`
  (räumt halbe Klone beim Start). Geprüft: 36 Engine-Tests gegen lokale Bare-Repos, 16 Queue-Tests,
  3 Gerätetests auf dem Telefonspeicher (Debug und R8).
- **M5** Standard-Oberfläche: **Entdecken** (alle Konten, Suche, Filter, Sortierung, Mehrfachauswahl, Klon-Sheet
  mit Kollisionsregel `ClonePlanner`, Klon per Adresse), **Lokal** (Filter mit Zählern, Suche, Auswahl,
  Sammel-Update/-Push/-Abruf, „Alle aktualisieren“), **Repo-Detail** (Änderungen vormerken/verwerfen,
  `CommitSheetFragment`, Push, Update mit Banner für „Remote voraus“ und „Lokale Änderungen im Weg“),
  **Aktivität** (Fortschritt, Abbrechen, Wiederholen, Details), Vordergrunddienst + Benachrichtigungen.
  Auf dem Emulator Ende-zu-Ende geprüft gegen `git http-backend` mit Basic-Auth (Klon von drei Repos,
  Commit + Push, Sammel-Update FF/Merge, Konflikt-Anzeige, Push-Ablehnung).
- **M6** Datei-Ebene: Dateibaum (`FilesFragment`, Git-Markierungen via `FileStatusIndex`, Anlegen/Umbenennen/Löschen
  über `RepoFiles` mit Pfad-Schutz und `.git`-Sperre, Öffnen/Teilen über `FileProvider`), Viewer und Editor in einem
  Fragment (`FileViewerFragment` + `CodeEditText` mit Zeilennummern; Suchen/Ersetzen, Zeilensprung, Umbruch,
  Tastenleiste, Auto-Einrückung, Undo/Redo, Bildvorschau, Hinweise für Binär-/große Dateien; „Speichern und
  committen“). `TextFileIo` erhält Zeilenenden (LF/CRLF) und BOM, schreibt atomar und erkennt Änderungen von außen.
- **M7** Erweitert I: Bereich „Erweitert“ im Repo-Menü mit **Verlauf** (Filter, Commit-Detail, Diff mit Zeilennummern und
  Leerraum-Schalter), **Branches**, **Stash**, **Tags** und **Remotes**. Die vier Listen laufen über das gemeinsame
  Muster `ListSource` (`RowAction`/`Prompt`/`Confirm`/`Values`) mit `AdvancedListFragment`; Engine-Seite ist `GitAdvanced`
  (`JgitAdvanced` verteilt an `HistoryReader`, `BranchOperations`, `TagAndStashOperations`, `RemoteOperations`).
- **M8** Erweitert II: **Konfliktlösung** (`ConflictsFragment`: je Datei meine/andere/beide Seite, von Hand bearbeiten,
  als gelöst markieren; Fortsetzen, Commit überspringen, Vorgang abbrechen; Texte beim Rebase mit vertauschten
  Seiten in `ConflictTexts`), **Reset** (sanft/gemischt/hart mit zweiter Bestätigung), **Revert**, **Cherry-pick**
  im Commit-Detail, **Repo-Einstellungen** (`RepoToolsFragment`: Identität, Update-Strategie je Repo, Bereinigung beim
  Abrufen, `.gitignore`, `info/exclude`, nicht verfolgte Dateien entfernen, Git-Daten verdichten, Submodule).
  Engine: `ConflictOperations`, `RewriteOperations`, `ToolOperations`, `ConflictMarkers`, `IdentityConfigurer`.
  Geprüft: 355 JVM-Tests, Emulator-Abläufe (Merge-Konflikt lösen und fortsetzen, Revert, Reset, Aufräumen,
  Ausschlüsse, Verdichten, Update-Strategie).
- **M9** SSH: Schlüssel (`SshKeyStore`: erzeugen Ed25519/RSA 4096, importieren aus OpenSSH/PEM auch mit Passphrase,
  nur im `SecretVault`, nie als Datei), Server-Schlüssel (`KnownHostsStore` + `AppServerKeyDatabase`: Trust-on-first-use
  ohne Rückfrage mitten in der Verbindung; `PublishedHostKeys` kennt die Fingerabdrücke von github.com und gitlab.com),
  `SshTransports` (MINA SSHD über JGit, `GitTransports` im `JgitEngine`), Oberfläche (Einstellungen › SSH-Schlüssel,
  Bekannte Server, Fingerabdruck-Dialog in Repo-Detail und Aktivität, Schalter „Über SSH klonen“, Remote auf
  SSH/HTTPS umstellen). Geprüft: 419 JVM-Tests inkl. echtem MINA-SSH-Server (`GitSshTestServer`), Gerätetests (Debug und
  R8), Emulator-Ablauf gegen den Test-Server: Schlüssel anlegen, Klon über SSH mit unbekanntem Server, Vertrauen,
  Aktualisieren, geänderter Server-Schlüssel (harter Fehler), Umstellen auf HTTPS.
- **M10** Zusätze: **Teilen → Klonen** (`ShareTarget` nimmt nur strikt validierte HTTPS-Adressen, `MainActivity` ist `singleTask`),
  **Neues Repo** (`NewRepoSheetFragment`, `RepoCreator`: lokal initialisieren, Remote per API anlegen, `origin` setzen, erster
  Push; `GitProviderClient.createRepository`), **Blame** (`BlameOperations`, `BlameFragment`), **Syntaxfärbung** im Viewer und
  Editor (`SyntaxHighlighter` in `domain/syntax`, `SyntaxController` in der Oberfläche) und der **LFS-Spike** (Befund unten:
  LFS bleibt aus, die Oberfläche benennt es). Geprüft: JVM-Tests für alles Genannte, Emulator-Abläufe für jeden Zusatz.
- **M11** Politur und Auslieferung: Motion-Audit, Barrierefreiheit (Schrift 1,3/2,0, Dunkel, Tablet-Rail), Lint, Plurals, Einstellungen
  (Design, Systemfarben, Nur im WLAN, Über GitMax mit Lizenzen), Sicherheitsprüfung, Release-Build und -Test, `README.md`,
  `DESIGN.md`. Stand nach den Prüfdurchläufen (unten) und der Klartext-HTTP-Anpassung: 585 JVM-Tests, 10 Gerätetests (Debug und R8, API 35), signierte APK unter `builds/`.

## Zusätze (M10): Entscheidungen und Fallen

- **Syntaxfärbung braucht zwei Stufen.** `SyntaxHighlighter` zerlegt den ganzen Text einmal im Hintergrund (ein Durchlauf, keine
  Parser; Obergrenze 200 000 Zeichen). Auf das Feld kommen aber nur Spans für die Zeilen im und um das sichtbare Fenster
  (`SyntaxController`, 60 Zeilen Rand, höchstens 3 000 Spans), das Fenster wandert beim Scrollen mit. Die erste Fassung hängte
  alle Spans auf einmal an: bei einer 190 000-Zeichen-Datei stand der Hauptthread so lange, bis Android die App als nicht
  reagierend meldete. Das Entfernen der Suchtreffer-Farbe darf nie *alle* `ForegroundColorSpan`s löschen: Suchtreffer und
  Färbung haben darum eigene Span-Klassen (`MatchTextSpan`, `SyntaxSpan`).
- **Große Dateien öffnen im Viewer zäh** (um 190 000 Zeichen etwa 2,5 s Ruckeln), auch ohne Färbung. Das ist die Grundkosten des
  `TextView`-Layouts, nicht der Färbung; die Obergrenze von 2 MiB und der Hinweis „Trotzdem öffnen“ bleiben die Schutzgrenze.
- **Blame** liefert für noch nicht committete Zeilen kein Commit (`BlameLine.isCommitted()` ist dann `false`); die Oberfläche
  beschriftet sie als „Noch nicht committet“ (`blame_not_committed`). Obergrenze 20 000 Zeilen.
- **Neues Repo:** `RepoNames.check` prüft den Namen vor dem Netzwerkaufruf. Lehnt der Anbieter ab, macht `ProviderErrors.fromCreateResponse`
  aus HTTP 400/422 „Name schon vergeben“ (`NAME_TAKEN`, wenn die Antwort `already exists`/`already been taken` sagt) oder „Eingabe
  abgelehnt“ (`INVALID`); der Antworttext wird ohne Token auf 200 Zeichen gekürzt angehängt.
- **Teilen → Klonen:** `ShareTarget` ist die einzige Stelle, an der die App Text von einer fremden App annimmt: nur `https://`, keine
  Zugangsdaten in der Adresse (`@` im Host verwirft sie), höchstens 2 048 Zeichen, bei github.com-Webseiten bleiben Besitzer und Repo
  übrig. Der Text wird nie als Pfad oder Befehl benutzt; geklont wird erst nach Bestätigung im Klon-Sheet (`ARG_CLONE_URL` in Entdecken).

## Git LFS: Spike-Befund (M10) und Entscheidung: **aus**

GitMax lädt und sendet **keine** LFS-Inhalte. Eine LFS-Datei bleibt auf dem Handy der Zeiger (`version … / oid sha256:… / size …`);
`GitLfs` erkennt Zeiger und `filter=lfs` in `.gitattributes`, die Oberfläche benennt es: Banner im Repo-Detail, Hinweis im
Commit-Sheet, Hinweis über dem Zeiger im Viewer. Zeiger lassen sich normal committen und pushen.

Was der Spike (`LfsSpikeTest`, mit Test-LFS-Server `LfsSpikeServer`, Basic-Anmeldung) gezeigt hat:

- Machbar ist es **nur für HTTPS**: Repo-Config `filter.lfs.useJGitBuiltin`, `required`, `clean=jgit://builtin/lfs/clean`,
  `smudge=jgit://builtin/lfs/smudge` plus einmaliges `BuiltinLFS.register()`. Mit `useJGitBuiltin` allein läuft nichts: den
  Checkout steuert `filter.lfs.smudge`, nicht `LfsFactory`.
- **JGits LFS-Verbindung benutzt keinen `CredentialsProvider`.** Ohne Zutun gibt es 401 und der ganze Checkout bricht ab (die
  Datei bleibt 0 Byte). Zugangsdaten in der URL setzt JGit nicht in einen `Authorization`-Header um (das wäre ohnehin verboten).
  Gangbar wäre nur eine eigene `HttpConnectionFactory` (`HttpTransport.setConnectionFactory`), die den Header für Hosts mit
  Konto setzt; so bleiben Token aus URL und `.git/config` draußen, und fremde Hosts (Speicher-URLs der Anbieter) bekommen ihn nicht.
- Hochladen läuft beim Push über JGits Pre-Push-Hook (Batch, dann PUT; im Commit liegt nur der Zeiger).
- Gründe gegen v1: JGit meldet **keinen Fortschritt und kein Abbrechen** während des LFS-Downloads (ein 1-GB-Klon sähe eingefroren
  aus); der Speicher **verdoppelt** sich (`.git/lfs/objects` plus Arbeitskopie, Hardlinks gibt es auf dem Telefonspeicher nicht);
  für SSH-Remotes liefe `git-lfs-authenticate` über die globale `SshSessionFactory`, nicht über unsere pro Vorgang gesetzte
  (Ausweg: `lfs.url` auf die HTTPS-Adresse setzen); R8-Regeln für gson/`Protocol` sind auf dem Gerät nicht geprüft.
- Wer LFS später einschaltet, braucht: Abhängigkeit `org.eclipse.jgit.lfs` (+gson) in der App, Keep-Regeln, die Verbindungsfabrik
  mit Host-Beschränkung auf Konto-Hosts, Fortschritt aus den Batch-Antworten, Fehlerabbildung (401 → AUTH) und Gerätetests unter R8.
- `LfsSpikeTest` bleibt als Beleg im Repo; das LFS-Modul liegt nur auf dem Testklassenpfad (`testImplementation`), nicht in der APK.

## Politur und Auslieferung (M11): Entscheidungen und Fallen

- **Motion** (Audit nach `transitions-polish`): Dauern und Easing stehen nur dort, wo sie benutzt werden (`integers.xml`:
  `motion_micro/quick/fast`; ungenutzte Tokens wurden entfernt). Sheets und Dialoge öffnen langsamer als sie schließen (250 → 150 ms,
  eigene Fensteranimationen `anim/sheet_*`, `anim/dialog_*` über `bottomSheetDialogTheme` / `materialAlertDialogTheme`; Dialog mit
  Skala 0,96). Seitenwechsel verschieben 8 dp (`SlideDistanceProvider` in `Motion.sharedAxis`; das private Material-Maß zu überschreiben
  meldet Lint). Bewusst nicht übernommen: Unschärfe beim Seitenwechsel (Material kennt sie nicht) und Blur 2 dp beim Icon-Wechsel (hier gilt
  `make-interfaces-feel-better`: 4 dp; die beiden Skills widersprechen sich an dieser Stelle).
- **Große Schrift:** Ab Schriftgröße 1,5 stehen Aktionszeilen untereinander (`AdaptiveRow`: Repo-Kopf, Konflikt-Aktionen), Banner stapeln Text
  und Aktion, die Rail ist 96 dp breit. Geprüft wurde auf dem Emulator bei 1,3 und 2,0 (Lokal, Repo-Detail, Commit-Sheet, Entdecken, Aktivität,
  Einstellungen), im Dunkelmodus und bei 800 dp Breite (`adb shell wm size 1600x2560` + `wm density 320`, danach `wm size reset`).
- **Lint** (`lintDebug`) ist sauber; abgeschaltet sind nur `Typos` (deutsche Texte), die Versions-Hinweise, `PluralsCandidate` (Rest sind
  Wendungen), `Overdraw` (Fragment-Wurzeln brauchen den Hintergrund für Material-Übergänge) und `TrustAllX509TrustManager` (kommt aus JGit,
  nur für `http.sslVerify=false`). Offen bleiben zwei Warnungs-Arten: `NotifyDataSetChanged` (Diff-Listen werden komplett ersetzt) und
  `NestedWeights` (Diff-Seitenansicht). Zähltexte mit Einzahl stehen in `res/values/plurals.xml` („1 Datei“, „2 Dateien“).
- **Einstellungen:** `AppSettings` (Design, Systemfarben, Nur im WLAN) liegt im `gitmax_state`; `Appearance` setzt sie um. Systemfarben
  (Dynamic Color) müssen **nach** `installSplashScreen()` und vor `super.onCreate` angewendet werden, sonst überschreibt das Theme der App sie;
  ein Wechsel baut die Activity neu auf. Das Fenster-Hintergrund-Attribut zeigt dafür auf `?android:attr/colorBackground`.
- **Nur im WLAN:** `OperationQueue.enqueue` fragt eine `NetworkPolicy` (`AndroidNetworkPolicy`: gemessenes Netz = nicht erlaubt). Gesperrt scheitert
  der Vorgang sofort mit `GitFailureKind.METERED_NETWORK` (Hinweis plus „Trotzdem versuchen“). Wer so einen Vorgang wiederholt, öffnet
  10 Minuten lang ein Fenster für weitere Vorgänge (Stapel). Es wartet bewusst nichts in der Warteschlange: ein dauerhaft wartender Vorgang
  hielte Vordergrunddienst und Wakelock offen.
- **Sicherheit** (Durchsicht in diesem Meilenstein, `security-review` brauchte eine Git-Historie, es gibt noch keinen Commit):
  `HostBoundCredentials` gibt Login und Token nur an den Host des Kontos (JGits Provider fragt den Host nicht; nach einer Weiterleitung
  auf einen fremden Host mit „401“ hätte dieser das Token bekommen). `Http` verfolgt Weiterleitungen selbst, nur für `GET` und nur auf
  demselben Host und Schema. `PromptDialog` setzt `FLAG_SECURE`, wenn ein Feld geheim ist (SSH-Passphrase). `data_extraction_rules.xml` schließt
  Cloud-Sicherung und Geräte-Übertragung ganz aus. Geprüft ohne Befund: Vault (AES-GCM mit Name als AAD), Token-Schwärzung in Provider-Meldungen,
  keine Token in Logs, nur `MainActivity` exportiert, `FileProvider` nicht exportiert.
- **`BuildConfig.TEST_BUILD`** (nur `debug` und `minified`) lockert nur noch `FLAG_SECURE` (Screenshots der Prüfläufe). Der Build-Typ
  `minified` (R8 an, debug-signiert) lässt die Emulator-Abläufe gegen die geschrumpfte App laufen. Die Release-APK hat es aus.
- **Klartext-HTTP (selbst gehostete Server ohne HTTPS).** Ein GitLab, das nur HTTP auf Port 80 anbietet, ließ sich nicht verknüpfen: Das Manifest
  hatte `usesCleartextTraffic="false"`, und `AccountEndpoint` machte aus `http://host` stillschweigend `https://host` (die HTTPS-Adresse zeigte dann
  eine fremde „Service not found“-Seite). Jetzt: `res/xml/network_security_config.xml` erlaubt Klartext **auf Plattformebene für alle Hosts**, weil
  die Adresse erst zur Laufzeit feststeht (`domain-config` geht nur für feste Namen; Lint-Regel `InsecureBaseConfiguration` ist dort begründet
  unterdrückt); die Grenze zieht die App, in allen Build-Typen gleich:
  - `AccountEndpoint.fromHostInput` behält `http://` nur für selbst gehostete Server (nie github.com/gitlab.com); `isInsecure()` und
    `webBaseUrl()` leiten das Schema aus der gespeicherten `apiBaseUrl` ab, es gibt kein eigenes Feld.
  - Die Oberfläche verlangt eine Bestätigung (`ConnectAccountFragment.confirmInsecure`; `ConnectViewModel.connect` lehnt ohne sie ab),
    Kontenliste und -detail zeigen „(unverschlüsselt)“ und ein Banner.
  - **Zugangsdaten über HTTP nur für solche Konten:** `HostBoundCredentials(…, allowCleartext)` gibt Login und Token bei `http://` nur heraus, wenn
    das Konto so verknüpft ist; sonst könnte jemand im Netz auf eine `http://`-Adresse eines HTTPS-Kontos (Submodul, Remote) mit „401“ antworten und
    das Token im Klartext abholen. `AccountCredentials.forUrl` wirft dafür `GitFailureKind.INSECURE_TRANSPORT`. `TransportPolicy` regelt dasselbe
    für „Klonen per Adresse“ und die Remote-Umstellung (SSH → HTTP statt HTTPS bei solchen Servern).
  - Weiterleitungen: `Http` folgt nur gleichem Host und Schema (ein Server, der auf HTTPS umleitet, braucht die `https://`-Eingabe; die
    Fehlermeldung nennt beides), JGit folgt einem Schemawechsel nur auf HTTPS (`TransportHttp.isValidRedirect`, im Bytecode geprüft).
  - Nicht betroffen: `ShareTarget` (nimmt Adressen fremder Apps) bleibt strikt `https://`.
- **Predictive Back ist aus** (`enableOnBackInvokedCallback="false"`): Mit der Option blieben nach der Zurück-**Taste** (auch Drei-Tasten-
  Navigation, `adb shell input keyevent 4`) alte Ansichten stehen oder der Inhalt fehlte in der Zugänglichkeits-Hierarchie (Material-Übergänge,
  Fragment 1.8.5); mit echter Wischgeste war alles in Ordnung. Auf Android 16 gilt Predictive Back sonst für `targetSdk 36` automatisch.
  Erneut prüfen, wenn Fragment oder Navigation aktualisiert werden.
- **Erster Start** führt ohne Assistenten über Leerzustände mit nächster Handlung: „Lokal“ bietet „Ordner GitMax anlegen“, „Entdecken“ „Konto
  verbinden“, die Commit-Identität fragt der erste Commit ab. Der im Plan genannte Drei-Schritte-Onboarding-Assistent entfiel.
- **Ohne Freigabe „Zugriff auf alle Dateien“** gibt es keinen Ausweichordner im App-Speicher (so war es geplant): GitMax meldet es mit einem
  Knopf zur Systemeinstellung.
- **Signierung:** Keystore `keystore/gitmax-release.jks` (PKCS12, RSA 4096, Alias `gitmax`, gültig bis 2126) liegt im Projekt und ist per
  `.gitignore` ausgeschlossen; Pfad und Passwörter stehen in `local.properties`. Er muss gesichert werden (siehe `builds/BUILD-INFO.txt`).
  `builds/GitMax-0.1.0.apk` ist die ausgelieferte Datei.
- **Emulator-Erfahrungen:** Das Image API 36.1 (`GitMax_API36`) startet auf diesem Rechner nicht (Gast bleibt „offline“, belegt nur ~550 MB);
  `KCD_Pixel` (API 35, 2 GB) wird nach Stunden träge und gehört dann neu gestartet (`adb emu kill`). Skripte warten nach `pm clear` etwa
  9 s auf den Start. `rm -rf *` nach einem `cd` blockiert die Sicherheitsprüfung; frische Arbeitsordner statt Löschen nehmen.

## Prüfdurchlauf nach der Auslieferung: gefundene Fehler und neue Regeln

Eine vollständige Durchsicht (Build, Tests, Lint, alle Schichten gelesen, Abläufe auf dem Emulator) fand die folgenden Fehler. Jeder hat
einen Test oder eine Emulator-Prüfung; die Regeln gelten für künftige Änderungen weiter.

- **Merge nur bei sauberem Arbeitsbaum.** Ein Konflikt wird mit `reset --hard` zurückgenommen (Sammel-Update mit `ABORT`, „Merge abbrechen“); das
  verwarf auch nicht committete Änderungen in Dateien, die mit dem Merge nichts zu tun hatten (belegt: Datei sprang auf den alten Stand).
  `JgitEngine.merge` verweigert deshalb mit `DIRTY_TREE`, wenn der Baum Änderungen hat; „Zwischenspeichern und aktualisieren“ (`autoStash`) stellt
  sie vorher weg und holt sie nach dem Abbruch zurück. Die Abbruch-Dialoge nennen die nicht committeten Änderungen jetzt.
- **Kein Vorgang bleibt für immer „laufend“.** `OperationQueue.run` fing nur `RuntimeException`; ein `Error` (zu wenig Speicher bei großem Klon,
  fehlende Methode auf dem Gerät) ließ den Vorgang laufen, sperrte seinen Ordner und hielt den Dienst am Leben. Jetzt `RuntimeException | Error`;
  `cloneRepository` räumt den halben Klon im `finally` auf, nicht nur nach einer `Exception`.
- **`RepoScanner` übersprang Repos, die `android`, `build` oder `node_modules` heißen** (die Namen gelten nur Ordnern ohne `.git`).
- **Editor: ungespeicherte Eingaben überlebten keine Drehung.** `showText` überschrieb den wiederhergestellten Text mit dem Dateiinhalt; zudem
  speichert ein `EditText` standardmäßig seinen ganzen Text im Instanzzustand der Activity (große Dateien sprengen die Binder-Grenze). Jetzt
  `CodeEditText.setFreezesText(false)`, der Entwurf liegt im `FileViewerViewModel` (`keepDraft`, im `onDestroyView` gesetzt), und `textShown` gilt
  pro View (die Fragment-Instanz überlebt ihre View, etwa nach „Blame“ und zurück). Auf dem Emulator mit Drehung geprüft.
- **Klon-Sheet „vorhandene aktualisieren“** ignorierte die Update-Strategie des Repos (immer Merge); es fragt jetzt `RepoSettingsStore`.
- **`SshKeyStore.generate`** hielt die Sperre während der RSA-Erzeugung (Sekunden); Abfragen vom Hauptthread (`hasSshKey`) hätten gewartet.
- **`GitOperationService`** beendete sich mit `stopSelf()` auch dann, wenn gerade ein neuer Vorgang gestartet worden war; jetzt `stopSelf(startId)`
  (nur `onTimeout` beendet bedingungslos). **`SshKeysFragment.register`** fängt einen fehlenden Browser ab.
- **Neu, weil im Plan und bisher nicht erreichbar:** „Push erzwingen …“ und „Vom Gerät löschen …“ im Repo-Menü (`TypedConfirmDialog`: der Knopf wird
  erst frei, wenn der Repo-Name getippt ist). Ohne erzwungenen Push war Amend nach dem Push eine Falle (Push abgelehnt, „Aktualisieren“ erzeugte
  einen Merge). `RepoRemover` löscht nie einen Arbeitsordner und vergisst die Einstellungen des Repos.
  Wählt man im Commit-Sheet „Letzten Commit ändern“ bei schon gepushtem Commit, steht dort ein Hinweis auf „Push erzwingen …“ (`amend_hint`).
- **Editor: schon das Betreten des Bearbeitungsmodus galt als „geändert“.** `setTextIsSelectable` setzt den Text intern neu und löste den
  `TextWatcher` aus; beim Verlassen kam „Änderungen speichern?“, obwohl nichts getippt war. `applyMode` setzt dafür `suppressDirty`.
- **Bewusst offen** (nicht behoben): Die Auswahl-Aktionen in „Lokal“ kennen kein Löschen (nur das Repo-Menü); Dateien mit nur durch
  Groß-/Kleinschreibung verschiedenen Namen lassen sich im Dateibaum nicht umbenennen (der Telefonspeicher unterscheidet die Schreibweise nicht).
- **Testgeräte:** Am Rechner hängt gelegentlich ein echtes Handy. Alle `adb`- und Gradle-Aufrufe für Prüfläufe mit `ANDROID_SERIAL=emulator-5554`
  einschränken (`connected…AndroidTest` läuft sonst auf jedem Gerät und deinstalliert die App danach). Der Emulator als Hintergrundaufgabe
  endet nach zwei Stunden und muss neu gestartet werden.

## Prüfdurchlauf 2026-10-06: gefundene Fehler und neue Regeln

Zweite vollständige Durchsicht (alle Schichten gelesen, Oberfläche auf dem Emulator geprüft). 579 JVM-Tests grün, `lintDebug` ohne Fehler und mit
4 Warnungen (die beiden Arten aus M11). Jeder Fund unten ist behoben; die Regeln gelten weiter.

- **Leerzustände müssen scrollen.** Bei Schriftgröße 2,0 im Querformat schnitt der Leerzustand (Symbol, Titel, Text, Knopf) den Knopf ab. Alle `empty`-Blöcke
  (Lokal, Entdecken, Aktivität, Konten, Dateien, Listen, Konflikte) liegen jetzt in einem `NestedScrollView` mit `fillViewport`.
- **Knöpfe mit eigener Tönung brauchen Zustandslisten.** `RepoDetailAdapter.emphasise` setzte feste Farben: Die drei Hauptknöpfe sahen während eines Vorgangs
  bedienbar aus, ohne es zu sein. Jetzt `whenDisabled(…)` mit 12 % bzw. 38 % `colorOnSurface`.
- **`TextView.setTypeface(tf, NORMAL)` setzt nur `tf`.** `SimpleRowAdapter` nahm das zuletzt gesetzte (fette) Typeface als Basis, recycelte Zeilen blieben fett.
  Das Basis-Typeface wird im Holder gemerkt.
- **`PebbleView` setzt abgebrochene Übergänge auf ihren Endzustand** (`settleTransitions` beim Lösen vom Fenster): Ein beim Scrollen unterbrochener Stein blieb sonst
  halb umgefärbt, weil ein erneutes Binden mit demselben Zustand nichts mehr ändert. Ein unlesbares Repo zeigt in „Lokal“ den Stein `FAILED`, nicht `CLEAN`.
- **Bestätigungsdialoge für Zerstörendes:** DE „Merge abbrechen?“ hatte „Abbrechen“ als Bestätigung und „Schließen“ als Gegenknopf. Jetzt „Merge abbrechen“ /
  „Merge behalten“ (`repo_abort_keep`); der Verwerfen-Dialog nutzt `activity_cancel`. Das Umbenennen-Fenster hieß „Anlegen“ (jetzt „Umbenennen“).
- **Eingabefehler löschen:** `AccountDetailFragment` ließ „Name darf nicht leer sein“ auch nach dem erfolgreichen Speichern stehen.
- **Klon in Teilschritten (`BatchedClone`):** Der Rückfall „holt den Rest am Stück“ war wirkungslos (JGit überspringt Wants, deren Tracking-Refs schon stimmen; der
  Server schickt Inhalte zu Commits, die der Client „hat“, nicht noch einmal). Verweigert der Server einzelne Objekte oder fehlen danach Inhalte, liefert `run()`
  jetzt `Optional.empty()` und der normale Klon übernimmt. Die Pause zwischen zwei Klon-Versuchen prüft den Abbruch in 200-ms-Schritten.
- **`KeystoreSecretCipher.loadOrCreateKey` ist synchronisiert:** Zwei Threads beim allerersten Zugriff hätten zwei Schlüssel unter demselben Namen erzeugt.
- **Konfliktlösung „Beide behalten“ liest und schreibt ISO-8859-1** (die Marken sind ASCII): Eine Datei, die nicht UTF-8 ist, bekam sonst für jedes fremde Byte ein
  Ersatzzeichen. Test: `keepingBothSidesLeavesTheBytesOfAFileThatIsNotUtf8Untouched`.
- **Fehlerabbildung:** Statuscodes („401“, „403“) werden nur im Text ohne Adressen gesucht (ein Repo „projekt-403“ ist kein Berechtigungsproblem); ein
  `EOFException` zählt nur zusammen mit einer `TransportException` als Verbindungsproblem, nicht bei einer abgeschnittenen lokalen Datei.
- **Editor:** „Zu Zeile springen“ kopierte den ganzen Text je Zeile (quadratisch, in großen Dateien ein ANR); die Suche nutzt ein Muster statt `toLowerCase` (`„İ“`
  ändert die Länge, „Alle ersetzen“ traf dann die falschen Stellen); ein zweites Speichern vor dem Ende des ersten (`saving`) löste einen falschen
  „Datei geändert“-Dialog aus.
- **Einstellungen:** „Systemfarben“ baut die Activity erst nach dem Schreiben der Einstellung neu auf (vorher Wettlauf mit dem Hintergrund-Thread). „Rückgängig“ beim
  Entfernen eines Arbeitsordners stellt auch die Markierung „Standard“ wieder her (`WorkspaceViewModel.restore`).
- **Tests:** `JgitLargeFilesTest` setzte die JVM-weite `WindowCacheConfig` und stellte sie nie zurück (alle späteren Klassen liefen mit dem kleinen Schwellwert);
  `SshDeviceTest` ließ je Lauf zwei Preferences-Dateien in den App-Daten liegen. Neu: `PersistentCloneJournalTest` (löscht Ordner rekursiv, war ungetestet).
  `JgitLargeFilesTest` kann den Android-Fehler „Inflater has been closed“ auf der JVM nicht zeigen (das JDK beendet übergebene Inflater nicht): Den Schutz tragen
  `InflaterCacheTest` und die Gerätetests.
- **„Branches › Zusammenführen“ verweigert einen echten Merge bei lokalen Änderungen** (`BranchOperations.merge`, `DIRTY_TREE`), wie der Update-Pfad: Ein
  abgebrochener Konflikt (`reset --hard`) risse sonst unbeteiligte Änderungen mit. Vorspulen geht weiter (`FF_ONLY` bei schmutzigem Baum). Tests in `JgitAdvancedTest`.
- **Toter Code entfernt:** `PlaceholderFragment`, `fragment_placeholder.xml` und der Text `placeholder_body` (die Navigation zeigt längst die echten Ziele).

## Prüfdurchlauf 2026-10-07: gefundene Fehler und neue Regeln

Dritte Durchsicht (Domain, Speicher, Anbieter, Git-Engine, Vorgänge, ViewModels, Fragmente der Kernabläufe, Manifest und Ressourcen). 585 JVM-Tests,
10 Gerätetests (Debug und R8), `lintDebug` unverändert bei 4 Warnungen.

- **Zeitüberschreitungen sind keine Abbrüche.** `SocketTimeoutException` und JGits „Read timed out“ sind `InterruptedIOException`s; `GitErrorMapper` machte aus jeder
  davon `CANCELLED`. Ein Server, der fünf Minuten schwieg (der Testserver kappt lange Antworten), erschien als „Abgebrochen“, und weder `BatchedClone` noch der Klon
  wiederholten ihn. Jetzt: `isTimeout` → `NETWORK`; nur ein unterbrochener Thread oder das Abbruch-Flag zählen als Abbruch.
- **Klon-Adressen folgen dem Schema des Kontos** (`BaseProviderClient.alignScheme`): Ein GitLab hinter einem TLS-Proxy meldet oft `http://`-Adressen, ein reiner
  HTTP-Server `https://`; beides scheiterte beim Klonen (Verbindung bzw. Schutz vor Klartext). Gilt nur für Adressen auf dem Host des Kontos.
- **Teilen → Klonen** schneidet GitLab-Seitenadressen bei `/-/` ab (`…/projekt/-/tree/main` ergab sonst ein Repo „projekt/-/tree/main“).
- **Kein Neubeginn nach einem gescheiterten Klon in Teilschritten:** Er wiederholt Pakete selbst (8×); der äußere Klon-Versuch (3×) verwarf danach alles Geladene
  und begann von vorn. Der äußere Versuch gilt jetzt nur für den normalen Klon (`stepwiseFailed`).
- **„Lokal“ prüft Repos mit laufendem Vorgang nicht durch** (kein `git status` über einen Klon von Gigabytes während er schreibt).
- Kleinere Funde: Markierungen von Repos eines entfernten Kontos blieben in „Entdecken“ gezählt; `WorkspaceViewModel` konnte beim gleichzeitigen Entfernen mit einer
  `IllegalArgumentException` den Hintergrund-Thread (und damit die App) beenden; „Zu Zeile springen“ griff nach dem Zerstören der View auf `binding` zu.
- **Bekannt, nicht geändert:** `JsonFileStore` schreibt über eine feste Temp-Datei je Name (zwei gleichzeitige Schreiber desselben Caches könnten sich stören);
  `SharedPreferencesKeyValueStore.put` ignoriert das Ergebnis von `commit()` (bei vollem Speicher geht ein Eintrag still verloren); `AccountRepository.connect` setzt ein
  erneut verknüpftes Konto ans Ende der Liste; ein leerer, vorhandener Ordner gilt dem Klon-Planer als „belegt“ (der Klon selbst nähme ihn); die
  Repo-Detail-Meldung bei Netzfehlern hat keine „Erneut versuchen“-Schaltfläche (die Aktivität hat sie).

## Große Klone und Android-Besonderheiten (2026-10-05)

- **Androids `InflaterInputStream.close()` beendet auch einen übergebenen Inflater** (JDK nicht). JGit liest Dateien über der Stromgrenze (8 MiB)
  so und gibt den Inflater danach an `InflaterCache` zurück: „Inflater has been closed“, jeder Klon mit einer großen Datei brach beim Auschecken ab.
  Fix: `src/main/java/org/eclipse/jgit/lib/InflaterCache.java` ersetzt die JGit-Klasse (Inflater aus `get()` ignorieren `end()` von außen); in
  `build.gradle.kts` entfernt der Task `patchedJgit` die Original-Klasse aus dem JGit-Jar (`jgitOriginal`-Konfiguration, slf4j-api bleibt aus der App).
  Tests: `InflaterCacheTest`, `InflaterProbeTest` (Gerät), `JgitLargeFilesDeviceTest`.
- **Klon in Teilschritten** (`BatchedClone`): Erst `blob:none` (Commits und Bäume), dann die Dateiinhalte in Paketen per `want <sha>` (Größe nach Dauer
  und Bytes je Objekt, Ziel ≈ 200 MiB), jedes Paket bleibt liegen, bei Verbindungsabbruch wird nur das Paket wiederholt (bis 8×). HEAD zeigt erst am Ende
  auf den Branch. Rückfall auf den normalen Klon, wenn der Server keine Filter oder keine Einzelanforderung erlaubt. Grund: Der Testserver kappt
  HTTP-Antworten nach etwa 5 Minuten, und ein Klon ist eine einzige Antwort. `GitErrorMapper` prüft Netzfehler vor dem Textvergleich (Androids
  HTTP-Stapel meldet einen Abbruch als „not found: size=0“).
- **Prüfen auf dem Handy:** `adb install -r` für Debug- und Test-APK (kein `connected…AndroidTest`, das deinstalliert die App samt Konten),
  Tests mit `adb shell am instrument -w …`. `JgitHugeCloneDeviceTest` klont über `git daemon` + `adb reverse tcp:9418` (`-e hugeRepo name.git`).
- Der Emulator `KCD_Pixel` hat nur etwa 1 GB frei; große Klone brauchen das Handy.

## Zweisprachigkeit und Veröffentlichung

- **Sprachen:** `res/values/` ist Englisch (Standard, auch für alle anderen Gerätesprachen), `res/values-de/` Deutsch. Die Schlüssel beider
  Dateien (Texte und Zähltexte) und die Platzhalter je Schlüssel müssen übereinstimmen; ein kleines Skript, das beide Dateien parst, fand beim
  Übersetzen jede Abweichung. Das Gerät wechselt über Android (Sprache der App) oder `Languages` (Einstellungen › Sprache).
- **Prüfläufe in beiden Sprachen:** `adb shell cmd locale set-app-locales de.lembergmax.gitmax --locales de` (oder `en`) stellt die App ohne
  Gerätewechsel um; Emulator-Abläufe, die Texte antippen, brauchen die passende Sprache.
- **Das Repo ist öffentlich** (`github.com/lembergmax/GitMax`, MIT). `README.md` (Englisch) und `README.de.md` zeigen Screenshots aus
  `docs/images/` (erzeugt aus Emulator-Aufnahmen mit erfundenen Demo-Repos; die Skripte dafür liegen nicht im Repo). `PRODUCT.md` und
  `DESIGN.md` gibt es englisch, die deutschen Fassungen heißen `*.de.md`. Keystore, `local.properties`, APKs und `builds/` stehen in der
  `.gitignore`; vor jedem Veröffentlichen prüfen, dass keine Geheimnisse, Pfade oder Adressen in Dateien stehen.
- **Emulator-Falle für Testserver:** Schließt ein Python-Testserver die Verbindung gleich nach der Antwort (HTTP/1.0 oder `Connection: close`),
  kappt der Emulator-Netzstack größere Antworten („unexpected end of stream“). Testserver sprechen HTTP/1.1 mit Keep-Alive und schließen nie
  zuerst.

## Entscheidungen und Fallen aus der Umsetzung

- **Keine Theme-weite `fontFamily`.** AppCompat liest sie in einem zweiten Durchgang ohne Gewicht und
  verwirft `textFontWeight` jeder Text-Appearance (alle Überschriften wären normal). Die Familie steckt
  in jeder Text-Appearance, `Widget.GitMax.TextView` setzt BodyMedium als Standard.
- **Toolbars:** `app:title`, nicht `android:title` (wird sonst ignoriert).
- **Navigationsleiste nur auf den vier Hauptzielen**, Unterseiten haben einen Zurück-Pfeil.
- **Unit-Tests sehen nur `android.jar`:** `com.sun.net.httpserver` ist nicht verfügbar, deshalb nutzt
  `FakeServer` einen eigenen `ServerSocket`.
- **Emulator-Tests gegen Fake-Server:** Server-Eingabe `http://10.0.2.2:PORT` funktioniert in jedem Build (siehe „Klartext-HTTP“ oben; die
  Bestätigung kommt als Dialog). `FLAG_SECURE` auf Token-Bildschirmen ist im Debug-Build aus, damit Screenshots gehen.
- **Test-Werkzeuge** (nur lokal, im Scratchpad der Session): `uia.py` (UI-Automatisierung über
  `uiautomator`), `fake_github.py` (Fake-Anbieter, Port 8080), `make_repos.sh` (Test-Repos in allen
  Zuständen), `palette.py` (Farbtokens), `apicheck.py` (statische API-Prüfung).

## Git-Engine: Fallen

- **`AppSystemReader` muss `openUserConfig`/`openSystemConfig`/`openJGitConfig` überschreiben**, nicht nur
  `getUserConfig`/`getSystemConfig`: Ein `Repository` liest seine Konfiguration über die `open…`-Methoden.
  Ohne das zieht ein Test auf dem Entwicklerrechner dessen globale `core.autocrlf` (CRLF im Arbeitsbaum)
  und `gc.auto=0` gälte in der App nicht für Repo-Konfigurationen.
- **Test-Naht:** `JgitEngine` hat einen zweiten Konstruktor mit `Function<RemoteUrl, String>` (Adresse →
  was JGit öffnet). Tests lenken `https://example.invalid/max/alpha.git` damit auf ein lokales Bare-Repo.
- **`src/testSupport/java`** (nur in `debug` und `minified` eingebunden, nie im Release) enthält
  `GitFixtures`: JGit-Aufrufe für Gerätetests gehören in die App, weil R8 im Build-Typ `minified` JGit
  obfuskiert und der Test-APK es sonst nicht mehr findet. Gerätetests nutzen deshalb nur öffentliche
  App-Klassen und `GitFixtures`.
- **Klon in Ordner mit verbotenen Dateinamen** (`a:b.txt`) scheitert mit `INVALID_PATH` und räumt auf
  (getestet). Ein toleranter Checkout, der solche Dateien einzeln meldet statt abzubrechen, fehlt noch.
- **`FileUtils.delete(..., IGNORE_ERRORS)`** räumt halbe Klone; der Pfad steht bis zum Ende im
  `CloneJournal`, damit der nächste Start ihn löscht, falls der Prozess dazwischen stirbt.
- **Zeilenenden:** Quellen sind LF. Der Edit-Werkzeug-Pfad kann CRLF schreiben, `.gitattributes`
  (`* text=auto eol=lf`) hält das im Repo gerade.

## Git-Engine: Fallen aus M7/M8

- **`IdentityConfigurer.ensure`** gleicht `user.name`/`user.email` des Repos bei jedem Commit-erzeugenden Vorgang
  (Merge, Rebase, Cherry-pick, Revert, Stash, Konflikt-Fortsetzen) mit der gültigen Identität ab und überschreibt
  Abweichungen: Wer die Identität in den Repo-Einstellungen ändert, erwartet sie auch bei diesen Vorgängen.
  Ohne bekannte Identität bleibt die Konfiguration unberührt.
- **Beim Rebase sind „ours“ und „theirs“ vertauscht** (ours = Ziel-Branch, theirs = der aufgesetzte Commit).
  `Resolution.OURS/THEIRS` bleibt Gits Sprache; die Beschriftung in der Oberfläche kommt aus `ConflictTexts`.
- **Konflikt-Stufen** im Index: `DirCache.getEntriesWithin(path)` liefert nur Einträge *unter* einem Ordner, nicht die
  Stufen einer Datei. Für Stufe 2/3 einer Datei `findEntry(path)` und von dort die Einträge gleichen Pfads ablaufen.
- **Cherry-pick fortsetzen** behält den Autor des übernommenen Commits (aus `CHERRY_PICK_HEAD`); Revert und Merge
  schreiben den aktuellen Nutzer als Autor.
- **`CleanCommand.setPaths`** erwartet Ordner ohne Schrägstrich, die Vorschau (`setDryRun`) liefert sie mit.
- **`SubmoduleStatusType.INITIALIZED`** heißt „ausgecheckt und auf dem erwarteten Stand“, `REV_CHECKED_OUT` „ausgecheckt,
  aber anderer Stand“.
- **`GC.packRefs()` ist nicht öffentlich**: Verdichten läuft als `git.packRefs().setAll(true)`, `GC.repack()`,
  `prunePacked()` und `prune(emptySet)`. Nie `Git.gc()` (ProcessHandle, siehe oben).
- **Frisch von JGit angelegte Repos haben keinen `.git/info`-Ordner**; `writeExclude` legt ihn an.
- **Python-Hilfsskripte** lesen und schreiben Quellen mit `encoding="utf-8"` (sonst entsteht Windows-ANSI und der
  Compiler meldet „Nicht zuordenbares Zeichen“); für Java-Quelltext mit `\n` das Edit-Werkzeug nehmen.

## SSH: Entscheidungen und Fallen

- **Ed25519 braucht `net.i2p.crypto:eddsa`**: MINA SSHD 2.19 erkennt das JDK-EdDSA nicht (auch nicht auf JVM 21) und
  nimmt nur diese Bibliothek oder BouncyCastle. Die R8-Regel `-keep class net.i2p.crypto.eddsa.**` ist nötig, weil MINA
  sie per `Class.forName` sucht. Das bcrypt-KDF geschützter OpenSSH-Schlüssel läuft ohne BouncyCastle.
- **Schlüssel verlassen den Speicher nie**: `SshdSessionFactoryBuilder.setDefaultKeysProvider` liefert die `KeyPair`s aus dem
  `SshKeyStore`; `setDefaultIdentities` und `setDefaultKnownHostsFiles` sind leer, `~/.ssh` und `ssh_config` des Geräts
  spielen keine Rolle. Eine Passphrase wird nur beim Import zum Entschlüsseln gebraucht und nicht gespeichert.
- **Host-Schlüssel ohne Rückfrage mitten in der Verbindung**: `AppServerKeyDatabase.accept` wirft bei unbekanntem oder
  geändertem Schlüssel eine `HostKeyRejectedException` (liegt in `domain/`, der Mapper findet sie in der Ursachenkette).
  Der Vorgang scheitert mit `HOST_KEY_UNKNOWN`/`HOST_KEY_CHANGED` und dem Host in `paths()`; die Herausforderung liegt im
  `KnownHostsStore`, die Oberfläche zeigt den Fingerabdruck (`HostKeyDialog`), nach „Vertrauen“ wird der Vorgang erneut
  eingereiht. Ein geänderter Schlüssel wird nie von selbst übernommen; nur ein veröffentlichter Fingerabdruck von
  github.com/gitlab.com wird ohne Rückfrage angenommen und ersetzt dann auch einen älteren Schlüssel derselben Art.
- **Host-Kennung** ist der Hostname, bei Port ungleich 22 `[host]:port` (`HostIds`). `GitSshTestServer`s Standard-Port 22 ist
  auf dem Entwicklerrechner ohne Adminrechte bindbar; der Emulator erreicht ihn als `10.0.2.2`.
- **Klonen über SSH** wandelt die HTTPS-Adresse mit `RemoteUrl.toSshUrl()` um (`git@host:pfad.git`); ein eigener SSH-Port
  einer selbst gehosteten Instanz geht dabei verloren und lässt sich über „Adresse ändern“ in den Remotes setzen.
- **Test-SSH-Server für Emulator-Läufe** (`src/test/.../GitSshTestServer`, `main`): `run_ssh_server.sh` im Scratchpad
  startet ihn mit dem Klassenpfad aus dem Gradle-Cache; eine Zeile `*` in der Datei der erlaubten Schlüssel lässt jeden
  Schlüssel herein. Der Server hält den Terminal-Aufruf offen: im Hintergrund starten.
- **Der Emulator hat in dieser Umgebung kein Internet**: `SshDeviceTest.githubIsRecognized…` wird dort übersprungen (nicht
  grün gemeldet). Die Fingerabdrücke aus `PublishedHostKeys` stammen aus der Dokumentation der Anbieter (4.10.2026).

## Oberfläche: Entscheidungen

- **Auswahl** (Entdecken und Lokal): Tipp = Aktion der Zeile (Klon-Sheet bzw. Detail), langes Drücken beginnt die
  Auswahl; in der Auswahl toggelt Tipp. Die Kachel zeigt dann ein Häkchen (`Motion.swapIcon`: Skalierung
  0,25→1, Alpha, Unschärfe 4→0 dp).
- **Zeitangaben** über `RelativeTime` (deutsch), nicht `DateUtils`: sonst erscheinen auf englischen Geräten
  englische Texte in der deutschen Oberfläche.
- **ViewModels** halten ihren Zustand nur auf dem Hauptthread; Hintergrundarbeit meldet über `Handler.post`
  zurück. Zustände mit Fortschritt (Vorgänge) kommen als vollständiger Snapshot, nie als Delta.
- **Repo-Detail** zeigt nur den Fehler des *letzten* beendeten Vorgangs dieses Repos (ein neuerer Erfolg
  räumt ihn weg). Einzel-Update nutzt `ConflictPolicy.LEAVE` (Konflikt bleibt zum Lösen stehen), Sammel-Update
  `ABORT`.
- **Ladevorgänge zählen Generationen** (`loadGeneration`): mehrere `reload()` laufen auf dem Thread-Pool
  nebeneinander, ein langsames älteres Ergebnis darf ein neueres nicht überschreiben.
- **Datei-Editor:** ohne Umbruch liegt das `CodeEditText` in einem `HorizontalScrollView`, mit Umbruch direkt im
  Halter (ein `TextView` bricht in einem `HorizontalScrollView` nie um). Gemischte Zeilenenden, Dateien über 1 MiB und
  Nicht-UTF-8 sind schreibgeschützt bzw. werden nicht angezeigt, damit Speichern nie unbemerkt etwas verändert.
- **Emulator-Abläufe:** `connectedDebugAndroidTest` deinstalliert die App am Ende; danach fehlen Konto und
  Arbeitsordner (erst `flow_batch.sh`). `uia.py taprid "confirm"` trifft per Teilstring auch `confirm_push`.
- **Benachrichtigungs-Erlaubnis** wird einmalig vor dem ersten Vorgang erfragt (`NotificationGate`); ohne sie
  laufen Vorgänge weiter.

## Test-Werkzeuge für Emulator-Läufe (Scratchpad der Session, nicht im Repo)

- `flow_clone.sh`, `flow_repo.sh`, `flow_batch.sh`, `flow_failure.sh`: Emulator-Abläufe für M5 (Klonen, Commit/Push,
  Sammelaktionen, Fehlerpfade). **`make_repos.sh` und `tar` laufen mit `env -u MSYS_NO_PATHCONV`**, sonst kann
  `git.exe` die `/tmp`-Pfade nicht auflösen.
- `git_http_server.py` (Port 8081): reicht Anfragen mit Basic-Auth (`max` / `test-token-max`) an
  `git http-backend` weiter; `make_repos.sh` legt die Bare-Repos unter `%TEMP%/gm_repos/bare` an. Der
  Emulator erreicht beides unter `http://10.0.2.2:PORT`.

## R8 / Packaging

- `packaging { resources { excludes … } }` entfernt Eclipse-/OSGi-Metadaten (`/OSGI-INF/**` kollidiert
  zwischen den JGit-Jars). Die JGit-Meldungstexte (`JGitText*.properties`) bleiben.
- `proguard-rules.pro` hält JGits Übersetzungs-Reflection (`JGitText`, `TranslationBundle`) und MINA SSHD.
  R8-Warnungen zu `java.rmi`, `javax.security.auth.callback`, `org.apache.tomcat.jni` sind harmlos
  (`-dontwarn`).

## Regeln für den Code (aus dem `code`-Skill)

Blöcke immer mit Klammern und Rumpf auf eigener Zeile; Methodenköpfe mit Parametern mehrzeilig (jeder
Parameter eine Zeile, `) {` allein); `final` für Parameter und lokale Variablen; minimale Sichtbarkeit;
Abhängigkeiten über den Konstruktor; `Optional` statt `null` bei Rückgaben; `Objects.requireNonNull` an
den Rändern; keine Wildcard-Importe; Records für Wert-Objekte; kein toter oder auskommentierter Code.
`domain/` bleibt frei von Android-Importen.

## Geheimnisse

Token und SSH-Schlüssel nur über den `SecretVault` (Android Keystore). Nie in Logs, Remote-URLs,
`.git/config`, Backups oder Fehlermeldungen. Keystores und `*.apk` gehören nicht ins Repository
(`.gitignore`).
