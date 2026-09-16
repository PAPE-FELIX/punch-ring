<p align="center">
  <b>English</b> | <a href="README.ko.md">한국어</a>
</p>

# Punch Ring

Turns the camera cutout on an Android phone into a battery and signal gauge.
A transparent overlay draws a battery arc around the hole and layers cellular and Wi-Fi dots
below it, so the punch hole stops being dead space.

<p align="center">
  <img src="docs/demo.gif" alt="Punch Ring demo" width="400"><br>
  <sub>Battery arc, charge spin, status bubble, and the smiling face — recorded on a virtual punch hole</sub>
</p>

| Normal | Camera open |
|---|---|
| <img src="docs/sample-ring.png" width="300"> | <img src="docs/sample-smile.png" width="300"> |

**No network permission. No account. No server.** Everything is drawn from what the phone already knows.

## What it shows

- **Upper arc — battery.** Green while charging, mint with a slow brightness breath on fast charge,
  orange in power saving, red under 15%.
- **Lower dots — signal.** Cellular sits underneath as a lime base layer; Wi-Fi covers the same four
  coordinates in a brighter blue. VPN keeps the physical Wi-Fi reading, so the dots stay honest.
- **Status bubbles.** Charging, full, Bluetooth, hotspot, VPN, network switches and high battery
  temperature separate from the hole as a droplet, hold, then get absorbed back.
- **Connection motion.** On a Wi-Fi, charge or cellular change the arc spins one full turn, stretching
  up to 270° before returning to the real battery level.

## Interactions

- **Tap the ring** — opens the selfie camera. Only a small area around the hole receives touches;
  everything else passes through to the app underneath.
- **Long press** — device status panel, or the rear camera with a **smiling face** (switchable in settings).
  While the camera is open the arc rotates 180° in place to become a mouth and two signal dots slide
  along the same circle to become eyes. The face turns with the screen.

## Cutout compatibility

Android's `DisplayCutout` is used first. When several cutouts exist, the punch hole is chosen by size,
roundness and distance from the screen edge. The same coordinate logic covers phones, tablets,
foldables and both orientations. Manufacturers that hide cutout data fall back to top centre plus
manual fine tuning in settings, and foldables keep separate profiles for the cover and inner screens.

## Install

Download the APK from [Releases](../../releases) and install it. Then:

1. Allow **Display over other apps**, and notifications if you want status bubbles.
2. Optional: enable the **Punch Ring** accessibility service. It reads only the brightness near the top
   of the screen so the ring can switch between light and dark, and it enables ring touches.
   Captured frames are discarded in memory and never saved or transmitted.

Requires Android 12 (API 31) or newer.

## Settings

Position and thickness, separate diameters for the battery arc and signal dots, animation speed
(0.1–5×), style presets, status icon size and hold time, priority order, per‑profile calibration for
folded and unfolded screens, a layout guide, and JSON backup and restore.

The preview at the top of the settings screen draws a virtual punch hole, so every animation —
including the smiling face — can be tested without waiting for the real event.

## Permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | draw the ring over other apps |
| `FOREGROUND_SERVICE` | keep the overlay alive |
| `POST_NOTIFICATIONS` | the persistent overlay notification |
| `READ_PHONE_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE` | signal strength |
| `ACCESS_FINE_LOCATION`, `NEARBY_WIFI_DEVICES` | Android requires these to read Wi-Fi signal level |
| `BLUETOOTH_CONNECT` | Bluetooth connect and disconnect events |
| accessibility (optional) | automatic contrast and ring touches |

There is no `INTERNET` permission, so nothing can leave the device.

## Build

Gradle is not used. The build script drives `aapt2`, `javac`, `d8` and `apksigner` directly:

```sh
./scripts/build-android.sh      # → dist/Punch-Ring-Android.apk
```

Needs a JDK (17 or newer) and an Android SDK with `build-tools;36.0.0` and `platforms;android-36`.
The script picks up `ANDROID_HOME`, or falls back to the common install locations.

## License

[MIT](LICENSE). Bundled Pretendard Variable is licensed under the SIL Open Font License.
