# Roans-HyperOS-CN-Fixer

An Android app that fixes the two biggest problems of a Xiaomi **China ROM**. Built for imported devices where the Play Store, Google services and push notifications do not work out of the box.

**No root required.** The app uses [Shizuku](https://shizuku.rikumo.com/) for ADB shell access and the Storage Access Framework for file access.

---

## How the app is organised

Two tabs. **Fixes** is a list of every fix, tap one to open it. **Settings** holds Shizuku, battery and autostart, the setup guide and the limitations.

On first launch the setup guide opens by itself. You can replay it any time from Settings with **Start setup**, which does not reset what you configured.

---

## The fixes

### Push notifications
HyperOS on a China ROM is built around Mi Push and squeezes Google Play services. Push notifications stop as soon as the screen goes off.

The app repairs this with the same mechanisms as the proven [hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix) project:

- keeps `com.google.android.gms` in the hidden `MILLET_NO_RESTRICT_APP` list
- removes the Greezer limiter from GMS (`dumpsys greezer IM GMS disable`)
- sets the MIUI autostart AppOps per app (`10008`, `10053`)
- can set the battery policy per app (Optimized, Unrestricted, Restricted)

PowerKeeper rewrites that list, so a recovery task runs every 15 minutes. Per app settings survive, because Android stores those in AppOps itself.

The screen also has a **Run checks** button. It reads the values back from the device, so you see whether the repair is actually in place instead of trusting an exit code.

**Apply now** does not replace the schedule. It runs one pass immediately, while the background task keeps repeating it. The card shows **Last check**, so you can see the protection is still being renewed.

Settings also has a **Send test notification**. That one proves the local notification path: channel, permission, Do Not Disturb. It is not an FCM push. A real push needs this app's own Firebase project, a registration token and a service account that is allowed to send.

### Screenshot mover
Xiaomi writes screenshots to `DCIM/Screenshots`, which makes Google Photos treat them as camera photos. A `.nomedia` file does not work on Xiaomi.

There is nothing to configure: the source is always `DCIM/Screenshots` and the target is always `Pictures/Screenshots`, so Google Photos shows that as a separate device folder you can switch off.

The move runs with the Shizuku shell user, which means this fix needs no storage permission at all and no folder picker.

Background moving is a switch with an interval (15, 30, 60 or 120 seconds). It runs as a foreground service that polls the folder through Shizuku, so a screenshot leaves DCIM within seconds instead of waiting for the 15 minute WorkManager floor. The 15 minute worker stays as a safety net. The app does not need to be open for it.

Files with `.pending` in the name are skipped. The camera app writes a temporary file first and renames it afterwards.

### Circle to Search
Built in. There is no MiCTS to install and nothing to register: the app calls the hidden voice interaction API itself, the same call MiCTS used, with the Circle to Search entry point.

The Google app has to be the default assistant, because the call starts the session of the current assistant. The screen shows which assistant is set and links straight to that setting.

Two ways to trigger it: the **Trigger now** button, or the **Circle to Search tile** in the Quick Settings panel. The tile is the no-root replacement for the long press gesture, since only an Xposed module can hook that gesture.

---

## What the app **cannot** fix

These are listed inside the app on purpose, so nobody promises what cannot work:

| Problem | Why not |
|---|---|
| Android Auto | System stubs are missing, installing an APK is not enough |
| Google Wallet and NFC payments | Play Services detects the Chinese SKU and turns TapAndPay off |
| Power button to Gemini | Power key events go to the system, no app can intercept them |
| LTE Band 20 (800 MHz) | Hardware, missing on many CN models |
| eSIM | Not supported on CN models |
| Bootloader unlock | Effectively closed on CN models |

---

## Setup

1. Install [Shizuku](https://github.com/RikkaApps/Shizuku/releases) and start it through wireless debugging
2. Install the APK from [Releases](../../releases)
3. Open the app and follow the setup guide: it walks through Shizuku, access, battery and autostart
4. Allow unrestricted battery in Settings, and set Autostart on in the Xiaomi security app

For Circle to Search you also need the [Google app](https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox), set as the default assistant. Nothing else.

> After a reboot Shizuku has to be started again. Once it runs, the app repairs itself within 15 minutes. The app itself does not need to stay open for that.

---

## Build

This repo builds through GitHub Actions, so you need nothing locally.

**Local build:**

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

**CI:** every push to `main` builds a debug APK as an artifact named after the version. A `v*` tag attaches that same file to a GitHub Release.

**Signing:** every build uses one keystore, taken from the repository secrets `CNFIXER_KEYSTORE_B64`, `CNFIXER_KEYSTORE_PASSWORD`, `CNFIXER_KEY_ALIAS` and `CNFIXER_KEY_PASSWORD`. That is not cosmetic: Android refuses to install an APK signed with a different key over an existing install, which shows up as "conflicting signature". Keep that keystore safe. Without it the build falls back to a throwaway debug key and updates stop working.

---

## Technical notes

- Kotlin, Jetpack Compose (Material 3)
- minSdk 31, target and compileSdk 36
- Shizuku API 13.x for shell access
- WorkManager for the periodic recovery and the screenshot mover
- DataStore for settings
- No `QUERY_ALL_PACKAGES`: package visibility comes from a scoped `<queries>` entry on the FCM receiver

---

## Credits

- [dingwen07/hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix) for the documented FCM mechanisms (MILLET list, Greezer, AppOps codes)
- [parallelcc/MiCTS](https://github.com/parallelcc/MiCTS) for the Circle to Search trigger, which is not bundled

## License

GPL-3.0. See [LICENSE](LICENSE).
