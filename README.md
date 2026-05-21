# exam-portal

Auf dieser Plattform koennen Schueler Klassenarbeiten, Tests und Pruefungen hochladen und fuer andere Schueler zur Verfuegung stellen.

## Projektstatus

Die Website speichert Daten jetzt ueber eine Server-API direkt in GitHub:

- Stammdaten in `data/state.json`
- Upload-Dateien in `uploads/...`

## Projektdateien

- `index.html`
- `style.css`
- `script.js`
- `api/state.js`
- `api/action.js`
- `api/_lib/github-store.js`
- `admin-app/src/SchularchivAdmin.java`
- `admin-app/run-admin.bat`

## Funktionen

- Geschuetzter Zugang fuer Nutzer und Adminbereich
- Lehrer mit Kuerzel und Faechern in der Adminzentrale verwalten
- Lehrer auswaehlen, dann Fach, dann Klasse `5` bis `12`
- Uploads mit Lehrer, Fach, Jahr, Klasse und Datei
- Neue Uploads werden zuerst geprueft und erst danach freigegeben
- Klassenarbeiten stehen vor Tests
- Inhalte erscheinen erst nach Auswahl von Lehrer, Fach und Klasse
- Klassen ohne Inhalte sind rot markiert
- Unterstuetzte Dateien koennen direkt auf der Website als Vorschau angezeigt werden

## Wichtiger Hinweis

Wenn das GitHub-Repository oeffentlich ist, sind auch hochgeladene Dateien ueber ihre URL oeffentlich erreichbar. Fuer sensible Inhalte sollte das Projekt spaeter auf private Speicherung umgestellt werden.

## Empfohlene Bereitstellung

Am besten deployest du das Projekt auf Vercel. Dort bleibt dein GitHub-Token geheim, und trotzdem kann die Website sicher in dein Repository schreiben.

## Benoetigte Umgebungsvariablen

Im Hosting muessen diese vier Variablen gesetzt werden:

- `GITHUB_TOKEN`
- `GITHUB_OWNER`
- `GITHUB_REPO`
- `GITHUB_BRANCH`

Beispiel:

- `GITHUB_OWNER=fvsg-weferlingen`
- `GITHUB_REPO=exam-portal`
- `GITHUB_BRANCH=main`

## GitHub-Token

Erstelle auf GitHub einen Personal Access Token mit Rechten fuer das Repository, damit Dateien erstellt und aktualisiert werden koennen.

## Kurzablauf fuer Vercel

1. Repo bei Vercel importieren
2. Projekt deployen
3. Die vier Umgebungsvariablen setzen
4. Neu deployen
5. Website aufrufen und testen

## Lokale Pruefung

```powershell
npm run check
```

## Java-Adminprogramm

Das Admincenter liegt jetzt als separates Java-Programm vor.

Start unter Windows:

```powershell
.\admin-app\run-admin.bat
```
