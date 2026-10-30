# Lume – launcher leggero per Fire TV Stick (stile Google TV)

Niente pubblicità, niente tracker, nessuna dipendenza: solo framework Android.
Schede 16:9 arrotondate, anello bianco e zoom sul focus, orologio in alto,
preferite, nascondi app, info e disinstallazione.

## Telecomando
- **OK**: apre l'app
- **Pressione lunga su OK** oppure tasto **Menu (≡)**: preferite, sposta, nascondi, info, disinstalla
- **Indietro**: torna in cima (il launcher non si chiude)

## Ottenere l'APK

### Opzione A – GitHub Actions (nessuna installazione)
1. Crea un repository su GitHub e carica tutto il contenuto di questa cartella
   (compresa `.github`).
2. Vai su **Actions → Build APK → Run workflow** (parte anche da solo al primo push).
3. A fine build scarica `lume-launcher-apk` dagli Artifacts: dentro c'è `app-debug.apk`.

### Opzione B – Android Studio
1. File → Open → seleziona questa cartella, attendi la sincronizzazione.
2. Build → Build APK(s). Il file è in `app/build/outputs/apk/debug/app-debug.apk`.

## Installazione sulla Fire TV
```
adb connect IP_DELLA_STICK:5555
adb install app-debug.apk
```
Poi aprilo da "Le tue app" oppure impostalo come home.

## Note
- `targetSdk 28` è voluto: corrisponde a Fire OS 7 ed evita le restrizioni di
  visibilità dei pacchetti di Android 11+, così vedi tutte le app installate.
- Su **Fire OS stock** il tasto Home apre normalmente il launcher Amazon.
  Su una ROM Android TV (es. LineageOS) Lume si imposta come home da Impostazioni.
