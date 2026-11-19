<p align="center">
  <a href="https://pallavsharmaofficial.github.io/PhoneSaar/"><b>Website</b></a> ·
  <a href="https://pallavsharmaofficial.github.io/PhoneSaar/app/"><b>Open the web app</b></a> ·
  <a href="https://github.com/pallavsharmaofficial/PhoneSaar/releases/download/latest/phonesaar.apk"><b>Download the Android app</b></a>
</p>

# PhoneSaar

Make an Android phone or TV faster and easier on the battery, safely. PhoneSaar works over adb: from the Android app over wireless debugging (on the same phone, or another phone or TV on the same Wi-Fi), or from the web app over a USB cable.

**It never** deletes photos, clears app data, uninstalls apps or factory-resets. Turning anything off is always asked first, and every change can be undone in one tap.

## What it does

- **Boost**: closes background apps, trims storage, speeds up animations. There's also a home-screen widget and a Quick Settings tile.
- **Battery**: shows which apps drain the battery and heat the phone; can stop chosen apps running in the background.
- **Debloat**: finds preinstalled ad, tracking and bloat apps (Xiaomi, Samsung, OPPO, Vivo, Facebook stubs and more), explains each one, and turns off only the ones you pick.
- **Fix-ups**: location, touch & hold delay, app permissions, and more.
- **Undo**: every change is logged with its reverse command.

## Connect

| From | How |
|---|---|
| Android app, same phone | Developer options → Wireless debugging → Pair device with pairing code |
| Android app, another phone or TV | Its IP:port (and pairing code on Android 11+), or `adb tcpip 5555` devices |
| Web app (Chrome/Edge on a computer) | USB cable with USB debugging on |

Some phones block adb changes until you allow them: on **OPPO/realme/OnePlus**, turn on *Disable permission monitoring*; on **Xiaomi/Redmi/POCO**, turn on *USB debugging (Security settings)*.

## Repo

`android/` is the Kotlin app and widget, `web/` the web app (Vite + Tango WebUSB), `site/` the landing page, and `rules/` the shared safety rules, debloat catalog and test fixtures. The full contract is in [SPEC.md](SPEC.md).

MIT licensed.
