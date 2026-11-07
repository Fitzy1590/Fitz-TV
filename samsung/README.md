# Samsung TV clean-up kit

Tools to find a Samsung (Tizen) TV on your Wi-Fi, see what's installed, and cut
down its ads and tracking. Run them on a computer on the **same network as the TV**.

## What's possible on a Samsung TV (and what isn't)

| Goal | Samsung / Tizen |
|---|---|
| Find the TV on Wi-Fi | ✅ `fitztv.py discover` |
| List installed apps, flag bloat | ✅ `fitztv.py apps` |
| Remote control / open apps from a computer | ✅ `fitztv.py key`, `fitztv.py launch` |
| Stop ads & viewing tracking (ACR) | ✅ TV settings (`fitztv.py checklist`) + DNS blocklist |
| Delete apps you or the store added | ✅ on the TV: Apps > ⚙ > Delete |
| Uninstall built-in apps (TV Plus, Gaming Hub...) | ❌ needs root; no network API allows it |
| Replace the home screen with your own | ❌ Smart Hub can't be replaced on Tizen |

One-command "debloat and new launcher" guides are written for **Android / Google TV**
(Sony, TCL, Hisense, Philips, Chromecast), which allow `adb`. Samsung TVs don't.

## Steps

```bash
cd samsung
pip install -r requirements.txt

python3 fitztv.py discover     # finds the TV, saves its IP
python3 fitztv.py info         # model number and year
python3 fitztv.py pair         # press "Allow" on the TV
python3 fitztv.py apps         # what's installed; [bloat?] = candidates to delete/hide
python3 fitztv.py checklist    # privacy/ad settings to switch off on the TV
```

Then add [`blocklist/samsung-ads-tracking.txt`](blocklist/samsung-ads-tracking.txt)
to Pi-hole, AdGuard Home, NextDNS or your router's block list.

The TV must be switched on (not standby). If `pair` is refused, check
Settings > General > External Device Manager > Device Connection Manager.

## Undo

The script doesn't change anything on the TV by itself. To undo, turn the settings
back on, remove the blocklist from your DNS filter, and reinstall deleted apps from
the Apps store. `~/.fitztv.json` holds the saved IP and pairing token; delete it to
forget the TV.
