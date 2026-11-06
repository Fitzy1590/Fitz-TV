# Fitz-TV

Safe, reversible debloat + performance toolkit for an **NVIDIA Shield TV**, run from any PC on the same network over ADB.

**What it does** (app or scripts)
- **Snapshot first.** It saves package and settings state to `shield/snapshots/baseline` before changing anything. `rollback.sh --apply` returns to it.
- **Debloat by disabling, never uninstalling.** It disables telemetry, feedback and preloaded promo apps (`shield/packages.txt`). Your own apps, every launcher, Play Store/Services, voice search, Chromecast, remote/controller pairing and OTA updates are hard-protected.
- **Instant UI.** All three Android animation scales are set to 0.
- **Ads/telemetry blocking** through encrypted Private DNS (AdGuard by default). Background Wi-Fi/BLE scanning is turned off.
- **Faster app launches.** Apps are optionally pre-compiled (`--optimize`).
- **One-click cleaner.** `clean.sh` (Mac/Linux) or `clean.cmd` (Windows double-click) clears all app caches and closes background apps.
- **Apps.** It installs Projectivy Launcher (ad-free, rotating wallpapers) and SD Maid SE (on-device cleaner).

Every script previews by default and only changes things with `--apply`.

## Fitz-TV Optimizer app (easiest)

An Android app that does everything above with buttons. Install it on your **phone** or directly on the **Shield**.

**Get it:** [`dist/FitzTV-Optimizer.apk`](dist/FitzTV-Optimizer.apk), or the **optimizer-latest** pre-release on the Releases page. Open either while logged into GitHub, download the file, then open it to install. Android will ask you to allow installs from your browser or file manager.

**On the Shield, one time:** Settings → Device Preferences → About → click **Build** 7×. Then Developer options → turn on **Network debugging**.

**From your phone:**
1. Open **Fitz-TV Optimizer**. The address is already set to `192.168.50.239`.
2. Tap **Connect & check status**. Accept **Allow network debugging** on the TV and tick *Always allow*.
3. Tap **Preview changes**, then **⚡ Optimise now**. It takes a snapshot first, then debloats, switches animations off, applies ad-blocking DNS and pre-compiles apps.
4. Tap **📺 Install this app on the Shield** to get the app and a one-click **Fitz Clean** tile on the TV.

**On the Shield itself:** open Fitz-TV Optimizer from the apps row. It targets itself (`127.0.0.1`). Accept the debugging prompt once.

**Buttons:** Clean cache & RAM · Roll back everything (with preview) · Install SD Maid SE · Wallpaper & picture tips.
**Fitz Clean** is a separate home-screen tile. One click clears every app's cache and closes background apps, shows how much was freed, and closes itself. Pin it to Projectivy's favourites row.

The app and the scripts below share the same engine (`shield/device/fitz.sh`) and package list (`shield/packages.txt`). They also share the same snapshot, which is stored on the Shield in `/data/local/tmp/fitztv/baseline`, so either one can roll back what the other did.

## Run from an Android phone (Termux)

No PC needed. The phone just has to be on the same Wi-Fi as the Shield.
1. Install **Termux** and **Termux:Widget** from [F-Droid](https://f-droid.org/packages/com.termux/). The Play Store build of Termux can lag behind.
2. On the phone's browser, log into GitHub, open this repo on branch `ccr-c74c2488-4326f9`, then tap **Code → Download ZIP**.
3. In Termux:
   ```bash
   termux-setup-storage            # allow access to Downloads
   pkg install -y unzip
   cd ~ && unzip -o ~/storage/downloads/Fitz-TV-*.zip
   cd ~/Fitz-TV-*/shield
   bash termux-setup.sh            # installs adb + home-screen shortcuts
   bash run-all.sh                 # snapshot -> preview -> y/N -> apply
   ```
4. The first time, accept **Allow network debugging** on the TV (tick "Always allow").
5. Add the **Termux:Widget** widget to your home screen. **Shield Clean** is your one-tap cleaner, and **Shield Rollback** undoes everything.

## 1. One-time setup (PC)

1. **On the Shield:** Settings → Device Preferences → About → click **Build** 7 times. Then go to Device Preferences → Developer options → turn on **Network debugging**. Note the IP shown under About → Status (or Network).
2. **On your PC:** install [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) so that `adb` is on your PATH. On Windows, use **Git Bash** to run the `.sh` scripts. On macOS, `brew install bash` (the scripts need bash 4+).
3. Put the Shield's IP in `shield/config.sh` (and in `shield/clean.cmd` on Windows).
4. The first time you connect, accept **"Allow network debugging"** on the TV (tick "Always allow").

## 2. Run it

```bash
cd shield
./run-all.sh            # snapshot -> preview -> asks y/N -> apply -> installs apps
```
Or step by step:
```bash
./snapshot.sh                     # baseline (first run) / timestamped snapshot
./debloat.sh                      # preview;  ./debloat.sh --apply
./tweaks.sh                       # preview;  ./tweaks.sh --apply --optimize   (--no-dns to skip DNS)
./install-apps.sh                 # opens Play Store on the TV; or drop APKs into shield/apks/
./clean.sh                        # one-click cleaner, run whenever you like
./rollback.sh                     # preview;  ./rollback.sh --apply  [snapshots/<dir>]
```
Edit `packages.txt` to keep anything. The `optional` tier (stock-launcher recommendation rows, Ambient screensaver) only runs if you add `optional` to `TIERS` in `config.sh`.

## 3. Launcher & 10-minute rotating wallpaper

The stock Android TV home screen can't show wallpapers and has a sponsored row. Projectivy fixes both. The stock launcher stays installed and untouched, so you can switch back at any time.
1. Open **Projectivy** → Settings → General → enable **Override current launcher**, and grant the accessibility permission it asks for. The Home button then opens Projectivy.
2. Settings → **Wallpaper** → pick a source (built-in, a folder of your own images, or a wallpaper plugin such as an Unsplash/Reddit provider from the Play Store). Set the change/refresh interval to **10 minutes**. If 10 isn't offered, use the closest value.
3. To recreate your current layout, add a **Favourites/Apps** row and the **channel rows** from your apps (Settings → Rows/Channels), then reorder them to match. Projectivy can also back up its layout.

## 4. Picture & streaming settings (set on the TV — not exposed to ADB)

Settings → Device Preferences → **Display & Sound**:
| Setting | Recommended | Why |
|---|---|---|
| Resolution | Your TV's native mode, e.g. 4K 59.940 Hz | Keeps the HDMI mode switches to a minimum |
| Advanced → Match content frame rate | **Seamless only** | Smooth, judder-free playback with no blank-screen pause. "Always" removes more judder but adds a 1–3 s black screen when playback starts |
| Advanced → Match content dynamic range | On if you watch HDR | Correct colours. Costs a short HDMI blink when the format changes |
| AI upscaling (your 2019 Shield supports it) | **AI-Enhanced, Medium** | Sharper low-res/1080p SDR video. Hardware processing, no noticeable lag |
| Audio → Surround passthrough | Auto / your receiver's formats | Bit-perfect audio |

Also: use **Ethernet** for 4K remux streaming if you can. In the stock launcher's Home screen settings, turn off **video/audio previews**.

**Privacy (manual):** Device Preferences → **Usage & diagnostics** → off. **Ads** (under Device Preferences or Privacy, depending on firmware) → opt out of personalisation and delete the advertising ID. Turn off any NVIDIA "share usage data" toggle. Location → off unless you need it.

## Notes & limits
- No app can clear other apps' caches or "boost RAM" on Android TV without root. `clean.sh`/`clean.cmd` do it properly through ADB. SD Maid SE does it by automating the Settings app, which may only partly work with the TV's Settings UI. RAM boosters generally make Android *slower*, because apps have to cold-start again. Run the cleaner when storage is low, not constantly.
- Private DNS blocks ads inside the launcher, many apps and telemetry. It **cannot** block YouTube's in-video ads, which are served from the same servers as the videos. If something stops loading, run `./tweaks.sh --apply --no-dns` or roll back.
- Firmware updates can re-enable disabled apps. Just re-run `./debloat.sh --apply`.
