# Schularchiv

Diese kleine Web-App ist komplett lokal und besteht nur aus:

- `index.html`
- `style.css`
- `script.js`

## Passwörter

- Seitenpasswort: `1208`
- Adminpasswort: `2702`

## Funktionen

- Zugang über Passwort `1208`
- Adminverwaltung oben rechts mit Passwort `2702`
- Lehrer mit Kürzel und Fächern in der Adminzentrale verwalten
- Lehrer auswählen, dann Fach, dann Klasse `5` bis `12`
- Uploads mit Lehrer, Fach, Jahr, Datum, Klasse und Datei
- Neue Uploads werden zuerst geprüft und erst danach freigegeben
- Klassenarbeiten stehen vor Tests
- Innerhalb der Listen wird nach dem neuesten Datum sortiert

## Wichtig

Die Daten werden im Browser über `localStorage` gespeichert. Das bedeutet:

- alles funktioniert lokal ohne Server
- die Daten bleiben im selben Browser erhalten
- auf einem anderen Gerät oder in einem anderen Browser sind die Daten nicht automatisch da
- große Dateien können je nach Browser-Speicherlimit problematisch werden
