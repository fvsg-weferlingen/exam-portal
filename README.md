# exam-portal

Auf dieser Plattform können Schüler Klassenarbeiten, Tests und Prüfungen hochladen und für andere Schüler zur Verfügung stellen.

## Projektdateien

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
- Uploads mit Lehrer, Fach, Jahr, Klasse und Datei
- Neue Uploads werden zuerst geprüft und erst danach freigegeben
- Klassenarbeiten stehen vor Tests
- Inhalte erscheinen erst nach Auswahl von Lehrer, Fach und Klasse
- Klassen ohne Inhalte sind rot markiert
- Unterstützte Dateien können direkt auf der Website als Vorschau angezeigt werden

## Wichtig

Die Daten werden im Browser über `localStorage` gespeichert. Das bedeutet:

- alles funktioniert lokal ohne Server
- die Daten bleiben im selben Browser erhalten
- auf einem anderen Gerät oder in einem anderen Browser sind die Daten nicht automatisch da
- große Dateien können je nach Browser-Speicherlimit problematisch werden
