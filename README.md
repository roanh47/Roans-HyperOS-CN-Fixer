# Roans-HyperOS-CN-Fixer

Een Android-app die de twee grootste pijnpunten van een Xiaomi **China-ROM** oplost. Gebouwd voor toestellen die geïmporteerd zijn en waar de Play Store, Google-services en push-notificaties niet vanzelf werken.

**Geen root nodig.** De app gebruikt [Shizuku](https://shizuku.rikumo.com/) voor ADB-shell-rechten en de Storage Access Framework voor bestandstoegang.

---

## Wat de app doet

### 1. FCM-notificaties — `FCM`-tab
HyperOS op een China-ROM is gebouwd rond Mi Push en knijpt Google Play services af. Daardoor vallen push-notificaties weg zodra het scherm uit gaat.

De app repareert dit met dezelfde mechanismen als het bewezen [hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix)-project:

- houdt `com.google.android.gms` in HyperOS' verborgen `MILLET_NO_RESTRICT_APP`-lijst
- haalt de Greezer-limiter van GMS af (`dumpsys greezer IM GMS disable`)
- zet per app de MIUI-autostart AppOps (`10008`, `10053`)
- kan per app de batterijpolicy zetten (Optimized / Unrestricted / Restricted)

Omdat PowerKeeper de lijst herschrijft, draait er een herstelstaak elke 15 minuten. Per-app-instellingen blijven staan, want die slaat Android zelf op in AppOps.

### 2. Screenshots — `Screenshots`-tab
Xiaomi zet screenshots in `DCIM/Screenshots`, waardoor Google Photos ze bij de camerafoto's gooit. Een `.nomedia`-bestand werkt op Xiaomi niet.

De app verplaatst nieuwe screenshots naar een map die je zelf kiest (bijvoorbeeld `Pictures/Screenshots`), zodat het in Google Photos een **aparte device-folder** wordt die je los kunt uitzetten.

Bewust **zonder** `MANAGE_EXTERNAL_STORAGE`: alleen een persistente SAF-grant op precies twee mappen. Dat is de val waar FolderSync in loopt.

Bestanden met `.pending` in de naam worden overgeslagen — de camera-app schrijft eerst een tijdelijk bestand en hernoemt dat daarna.

### 3. Circle to Search — `Zoeken`-tab
Werkt samen met [MiCTS](https://github.com/parallelcc/MiCTS). MiCTS kaal openen is op een CN-ROM niet genoeg: de assist-context ontbreekt en de Omni-aanroep faalt. Deze app registreert MiCTS daarom als **standaard assistent**, zodat de assist-gesture via MiCTS loopt.

HyperOS zet die waarde na elke reboot terug; de herstelstaak zet hem opnieuw zodra Shizuku draait.

---

## Wat de app **niet** kan oplossen

Deze staan bewust in de app vermeld, zodat niemand iets belooft wat niet kan:

| Probleem | Waarom niet |
|---|---|
| Android Auto | System stubs ontbreken; APK installeren is niet genoeg |
| Google Wallet / NFC betalen | Play Services herkent de Chinese SKU en zet TapAndPay uit |
| Power-knop → Gemini | Power-key events gaan naar het systeem; geen app kan die onderscheppen |
| LTE Band 20 (800 MHz) | Hardware — ontbreekt op veel CN-modellen |
| eSIM | Niet ondersteund op CN-modellen |
| Bootloader unlock | Bij CN-modellen praktisch dichtgetimmerd |

---

## Installatie

1. Installeer [Shizuku](https://github.com/RikkaApps/Shizuku/releases) en start hem via wireless debugging
2. Installeer de APK uit [Releases](../../releases)
3. Open de app en geef Shizuku-rechten
4. Zet de app op **Geen beperkingen** in batterijbeheer (Instellingen → Apps → CN Fixer → Batterijbesparingen)

Voor Circle to Search heb je daarnaast de [Google-app](https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox) en [MiCTS](https://github.com/parallelcc/MiCTS/releases) nodig.

> Na een reboot moet Shizuku opnieuw gestart worden. Zodra dat gebeurd is, herstelt de app zichzelf binnen 15 minuten.

---

## Bouwen

Deze repo bouwt via GitHub Actions; je hebt lokaal niets nodig.

**Lokaal bouwen:**

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug
```

De APK staat in `app/build/outputs/apk/debug/`.

**CI:** elke push naar `main` bouwt een debug-APK als artifact. Een tag `v*` hangt de APK aan een GitHub Release.

---

## Techniek

- Kotlin, Jetpack Compose (Material 3)
- minSdk 31, target/compileSdk 36
- Shizuku API 13.x voor shell-toegang
- WorkManager voor herstel taken
- DataStore voor instellingen
- Geen `QUERY_ALL_PACKAGES`: pakketzichtbaarheid via een scoped `<queries>`-entry op de FCM-receiver

---

## Credits

- [dingwen07/hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix) — de FCM-mechanismen (MILLET-lijst, Greezer, AppOps-codes) zijn daar publiek gedocumenteerd
- [parallelcc/MiCTS](https://github.com/parallelcc/MiCTS) — Circle to Search trigger (wordt niet meegebundeld)

## Licentie

GPL-3.0. Zie [LICENSE](LICENSE).
