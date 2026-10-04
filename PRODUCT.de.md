# Product

[English](PRODUCT.md)

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Native Android, Java 21, XML-Views mit Material 3, kein Kotlin und keine Compose. Entschieden in der
Planungsrunde (siehe `CLAUDE.md`); das Schwesterprojekt-Muster (KCD-Anwesenheit, KCD-Abrechnung,
RechenMax) ist die Vorlage.

## Users

Zunächst genau eine Person: der Autor der App, der am Handy an eigenen Projekten arbeitet. Er nutzt
GitHub und GitLab, kennt Git und will keine Anfängerführung. Die App ist als Quellcode veröffentlicht;
wer Git und Personal Access Tokens kennt, kann sie bauen und per Sideload installieren. Sie läuft auf
Android 14 oder neuer, unterwegs und am Schreibtisch, in wechselndem Licht, deshalb folgen Hell und
Dunkel dem System.

## Product Purpose

Git auf dem Handy so einfach machen wie auf dem Rechner: Konten von GitHub und GitLab verknüpfen,
Projekte einzeln oder gebündelt in einen Ordner auf dem Telefon klonen und aktualisieren, Änderungen
ansehen, committen und pushen. Erfolg heißt: Ein Repo ist mit wenigen Tippern auf dem Stand des
Remotes, und eine Änderung ist ohne Umweg gepusht. Alles darüber hinaus (Verlauf, Branches, Stash,
Konflikte …) ist da, drängt sich aber nicht vor die vier Standardhandlungen.

## Positioning

Ein Git-Client, der Repos in einem **öffentlichen Ordner** des Telefons ablegt, damit Dateimanager,
Editoren und Termux sie sehen, und der ganze Gruppen von Repos in einem Zug klont oder aktualisiert.
Klonen, Update, Commit und Push stehen vorn, der Rest liegt eine Ebene tiefer.

## Operating Context

- Repos liegen als normale Ordner im Telefonspeicher (Freigabe „Zugriff auf alle Dateien“).
- Authentifizierung über Personal Access Tokens je Konto, optional SSH-Schlüssel.
- Lange Vorgänge (Klonen) laufen in einem Vordergrund-Dienst weiter, wenn die App im Hintergrund ist.
- Bearbeiten geschieht teils in anderen Apps, teils im eingebauten einfachen Editor.
- Getestet wurde auf einem Emulator (API 35); echte Geräte und echte Konten folgen.

## Capabilities and Constraints

- Standard: Klonen (einzeln oder mehrere in einen Ordner), Update (einzeln oder alle), Commit, Push.
- Erweitert: Verlauf, Diff, Branches, Stash, Tags, Remotes, Merge und Konfliktlösung, Reset/Revert/
  Cherry-pick, Submodule, SSH, neues Repo, Dateibaum mit Viewer und Editor.
- Telefonspeicher kennt keine Symlinks, keine Hardlinks und keine Dateinamen mit `: ? * " < > | \`.
- Keine Git-Hooks, keine GPG-Signierung, kein Partial Clone. Git LFS ist bewusst aus: LFS-Dateien bleiben Zeiger-Dateien und die
  Oberfläche benennt das (Befund des Spikes in `CLAUDE.md`).
- Oberfläche Englisch (Standard) und Deutsch, wählbar in den Einstellungen. Keine Analytics, kein Crash-Reporting, nur Netzwerkzugriffe zu den eigenen
  Git-Hosts und deren APIs.
- Token und Schlüssel liegen nur im Android Keystore, nie in Logs, URLs oder Backups.

## Brand Commitments

Name **GitMax**, Package `de.lembergmax.gitmax`. Ton vom Nutzer gesetzt: **weich-freundlich**
(zugänglich, runde Formen, warme Farben, viel Luft), ohne dass Dichte und Klarheit eines Werkzeugs
darunter leiden. Kein Konfetti, keine Maskottchen.

## Evidence on Hand

Keine echten Nutzer-, Kunden- oder Leistungsdaten und keine Markenassets außer dem Namen. Demodaten gibt
es nur in Tests und sind dort als synthetisch gekennzeichnet; im Release erscheinen keine erfundenen
Zahlen oder Beispiel-Repos.

## Product Principles

1. Die vier Standardhandlungen (Klonen, Update, Commit, Push) sind immer in höchstens zwei Tippern
   erreichbar; Erweitertes verdrängt sie nie.
2. Nie stille Datenverluste: Konflikte, überschreibende Updates und Force-Push verlangen eine
   ausdrückliche, verständliche Entscheidung.
3. Stapelvorgänge sind robust: Ein fehlgeschlagenes Repo hält die übrigen nicht auf und bleibt
   wiederholbar.
4. Zustand ist ablesbar, ohne zu lesen: Status eines Repos und Fortschritt eines Vorgangs erscheinen am
   selben Ort und in derselben Form.
5. Geheimnisse bleiben geheim, Fehlermeldungen nennen das Problem und den nächsten Schritt.

## Accessibility & Inclusion

Material-Standard: Kontrast ≥ 4,5 : 1 für Text, 48-dp-Ziele, Schrift bis 200 %, TalkBack-Beschriftung für
Status und Auswahl, Farbe nie alleiniger Informationsträger, System-Einstellung „Animationen entfernen“
wird respektiert.
