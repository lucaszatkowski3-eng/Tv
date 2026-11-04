# ScreenOS

Android-TV-Dashboard mit lokaler Handy-Fernbedienung.

## Funktionen der aktuellen Entwicklungsfassung
- Vollbild-Dashboard für Android TV.
- Lokale Handy-Fernbedienung im WLAN auf Port 8765.
- TV-Befehle für Zurück, Home und Mediensteuerung.
- USB-Ordnerauswahl über Androids System-Dateiauswahl.
- Anzeige von Fotos und Videos aus dem ausgewählten USB-Hauptordner.
- Spotify-Client-ID-Eingabe auf der Handyseite und OAuth 2.0 Authorization Code mit PKCE.
- Verschlüsselte Speicherung der Spotify-Tokens über Android Keystore.
- Anzeige des aktuell laufenden Titels und automatische Token-Erneuerung.
- GitHub Actions APK-Build.

## Spotify einmalig einrichten
1. Erstelle eine Spotify-App mit Web API.
2. Hinterlege als Redirect URI exakt: https://lucaszatkowski3-eng.github.io/Tv/spotify-callback.html
3. Öffne in den Repository-Einstellungen Settings > Pages und wähle als Build and deployment source „GitHub Actions“.
4. Nach dem Pages-Workflow ist die Callback-Seite erreichbar.
5. Starte ScreenOS am TV, öffne die dort angezeigte Handy-Adresse im selben WLAN und trage die Spotify Client-ID ein.
6. Autorisiere Spotify. Kopiere danach Code und State von der Callback-Seite zurück in die Fernbedienung.

ScreenOS fragt kein Client Secret ab. Die Spotify-Verbindung verwendet PKCE. Welche Daten Spotify bereitstellt und ob die API für das Konto verfügbar ist, richtet sich nach den jeweils geltenden Spotify-Entwicklerregeln und Kontovoraussetzungen.

## Handy-Fernbedienung
TV und Handy müssen im selben WLAN sein. Die App zeigt auf dem TV die Adresse im Format http://TV-IP:8765 an. Diese Adresse im Browser des Handys öffnen.

## APK
Der Workflow „Build ScreenOS APK“ baut app-debug.apk als Testversion. Vor einer Veröffentlichung sind ein Release-Schlüssel, Tests am konkreten Fernseher und eine Datenschutzprüfung erforderlich.
