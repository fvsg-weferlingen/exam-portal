# exam-portal

Auf dieser Plattform koennen Schueler Klassenarbeiten, Tests und Pruefungen hochladen und fuer andere Schueler zur Verfuegung stellen.

## Was jetzt anders ist

Die Website speichert Daten nicht mehr nur lokal im Browser. Lehrer, Freigaben und Uploads werden jetzt ueber eine Server-API direkt in GitHub gespeichert:

- Stammdaten in `data/state.json`
- Upload-Dateien unter `uploads/...`

## Projektdateien

- `index.html`
- `style.css`
- `script.js`
- `api/state.js`
- `api/action.js`
- `api/_lib/github-store.js`

## Passwoerter

- Seitenpasswort: `1208`
- Adminpasswort: `2702`

## Wichtiger Hinweis

Wenn das GitHub-Repository oeffentlich ist, sind auch hochgeladene Dateien ueber ihre URL oeffentlich erreichbar. Fuer sensible Inhalte sollte das Projekt spaeter auf private Speicherung umgestellt werden.

## Empfohlene Bereitstellung

Am besten deployest du das Projekt auf Vercel. Dort bleibt dein GitHub-Token geheim, und trotzdem kann die Website sicher in dein Repository schreiben.

## Benötigte Umgebungsvariablen

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
