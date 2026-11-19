# PhoneSaar spec

PhoneSaar makes an Android phone or TV faster and lighter on battery over adb, without ever losing a login, a photo or an app's data. It has two clients that share one rule set:

| Client | Connects by | Lives in |
|---|---|---|
| Android app (APK) + home-screen widget | Wireless debugging on **this** phone (pair code + port), or **another** phone/TV by IP:port (Android 11+ pairing, or legacy `adb tcpip 5555`) | `android/` |
| Web app | USB cable from desktop Chrome/Edge (WebUSB) | `web/` |

A browser cannot open raw TCP sockets, so the web app is USB only and says so on its connect screen; it points Wi-Fi users to the APK.

## Safety contract (both clients, enforced in the command layer, not just the UI)

1. **Forbidden commands** are refused before they reach the device: every regex in `rules/rules.json` → `forbidden`. They include `pm clear`, `pm uninstall`, `cmd package uninstall`, factory reset (`recovery --wipe`, `am broadcast ... MASTER_CLEAR`), `rm` outside the two allowed thumbnail paths, `wipe`, `reboot bootloader|recovery`, and `settings put ... adb_enabled 0`.
2. **Protected packages** (`rules.json` → `protected`, regexes) can never be disabled or background-restricted, and never appear as debloat candidates: launchers (incl. PatchWall, Google TV), Play Services/Store/GSF, Google account and sync, `com.xiaomi.account`, System UI, Settings, phone, SMS, contacts, input methods, Bluetooth/remote/input services, camera, gallery, Wi-Fi/telephony providers, streaming apps, banking/UPI, ride-hailing and delivery driver apps, WhatsApp, and the PhoneSaar app itself.
3. **Ask before changing**: the boost actions run on one tap because they're reversible and lossless (listed below). Every app disable, background restriction or permission change needs an explicit per-item confirmation.
4. **Undo log**: every change is recorded as `{time, device serial, label, do-command, undo-command}`. The Undo screen reverts any single entry or all of them. The web app keeps the log in `localStorage` and offers it as a downloadable `.sh`; the APK keeps it in its private storage.
5. **Honest results**: every action reports what the device actually returned. A `SecurityException` is shown as "blocked by <OEM>", with that OEM's unlock hint from `rules.json` → `oemHints` (ColorOS: Developer options → Disable permission monitoring; MIUI: USB debugging (Security settings); Samsung: none). It is never shown as success.

## Features (same names and order in both clients)

1. **Connect**: see the table above. The APK discovers `_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp` with `NsdManager` and pre-fills ports, accepts a manual IP:port + 6-digit code, and remembers paired devices.
2. **Report** (read-only): model, manufacturer, Android version, RAM total/available, `/data` and SD card size/free, battery level/temp/health/charging, thermal status and the hottest sensor, animation scales, location mode, current top-CPU processes. "Before" is captured on connect and "after" after each action, shown side by side.
3. **Boost** (one tap, lossless): `am kill-all`; `sm fstrim`; animation scales → 0.5 (setting: 0.5 / 1.0 / off). Optional and **off by default**: `pm trim-caches 999G`, labelled "clears temporary app files; never logs out".
4. **Battery**: top drainers from `dumpsys batterystats --charged`, with UIDs mapped to packages via `cmd package list packages -U`. Per app: restrict background (`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`, undo `allow`), skipped for protected packages. Also shows heat causes (top CPU now, e.g. `dex2oat` or Play Store updating) with plain-language advice.
5. **Debloat**: enabled packages (`pm list packages -e`) matched against `rules.json` → `catalog`. Each candidate shows its description, vendor, risk (`safe` / `caution`), last used (from `dumpsys usagestats`), RAM use (from `dumpsys meminfo`), and an "already disabled" state. Disable with `pm disable-user --user 0 <pkg>`; undo with `pm enable <pkg>`.
6. **Fix-ups** (each confirmed): location on (`settings put secure location_mode 3`), animations, touch & hold delay (`settings put secure long_press_timeout 400`), grant a runtime permission to a chosen app, turn off permission auto-revoke (`appops ... AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore`). Blocked results show the OEM hint.
7. **Undo**: see the safety contract.

## Widget (APK)

A resizable 2×1 widget shows free RAM % and the last boost time, with one **Boost** button. A tap runs Boost through the saved self-connection when wireless debugging is reachable. Otherwise it falls back to `ActivityManager.killBackgroundProcesses` for every non-protected launchable package (needs the normal `KILL_BACKGROUND_PROCESSES` permission) and says "Quick boost (no wireless debugging)". There's also a Quick Settings tile with the same action.

## Shared rules

`rules/rules.json` is the single source of truth. The APK copies it into assets at build time (Gradle `copy` task); the web app imports it. JSON schema: `rules/rules.schema.json`. Shell-output fixtures live in `rules/fixtures/` and are **synthetic but format-exact**, so no real person's app list ends up in this public repo. Both clients' parser tests run against the same fixtures.

## Build and ship

- `android/`: application id `dev.pallav.phonesaar`, Kotlin, AGP 8.7.x, Kotlin 2.0.x, compileSdk/targetSdk 35, minSdk 26, Gradle wrapper 8.10.2, Jetpack Compose (Material 3). The adb client is `com.github.MuntashirAkon:libadb-android` (JitPack) with Conscrypt for Android 11+ TLS pairing. Unit tests: JUnit for parsers and the safety layer. Release signing reads `PHONESAAR_KEYSTORE`, `PHONESAAR_KEYSTORE_PASSWORD` and `PHONESAAR_KEY_ALIAS` from env; without them, release builds use the debug key, so local builds always work. No keystore is committed.
- `web/`: Vite + TypeScript (no framework), Tango (`@yume-chan/adb`, `@yume-chan/adb-daemon-webusb`, `@yume-chan/adb-credential-web`). Vitest for parsers and the safety layer. Built with base `/PhoneSaar/app/`.
- `site/`: static landing page (what it does, safety promise, "Open web app", "Download APK" → `https://github.com/pallavsharmaofficial/PhoneSaar/releases/download/latest/phonesaar.apk`, setup steps for wireless debugging with OEM notes). Light/dark.
- `.github/workflows/`: `ci-web.yml` (web test + build), `ci-android.yml` (android unit tests + `assembleDebug`), `deploy-pages.yml` (site + `web/dist` → `/app/`, on push to main), `build-apk.yml` (signed release APK → GitHub release `latest` as `phonesaar.apk`, on push to main touching `android/**` or `rules/**`).
