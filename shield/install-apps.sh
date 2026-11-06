#!/usr/bin/env bash
# Installs the extra apps. APKs dropped into ./apks/ are sideloaded; otherwise the
# Play Store page opens on the TV so you just press Install with the remote.
# Only ever installs — never removes anything.
. "$(dirname "$0")/lib.sh"
connect
APPS=(
  "com.spocky.projengmenu|Projectivy Launcher (ad-free launcher with rotating wallpapers)"
  "eu.darken.sdmse|SD Maid SE (on-device cache/storage cleaner)"
)
shopt -s nullglob
apks=("$HERE"/apks/*.apk)
for a in "${apks[@]}"; do log "Sideloading $(basename "$a")"; adb_ install -r "$a" || warn "install failed: $a"; done

mapfile -t INSTALLED < <(pkgs)
for entry in "${APPS[@]}"; do
  id="${entry%%|*}"; name="${entry#*|}"
  if in_list "$id" "${INSTALLED[@]}"; then log "$name already installed"; continue; fi
  log "Opening Play Store for $name — press Install on the TV, then press Enter here"
  sh_ am start -a android.intent.action.VIEW -d "market://details?id=$id" >/dev/null
  read -r -p "    (Enter when done, or type 's' to skip) " ans || true
  [ "$ans" = s ] && continue
  pkgs | grep -qx "$id" && log "$name installed" || warn "$name not detected. If the Store says it's incompatible, put its APK in shield/apks/ and re-run."
done
log "Next: open Projectivy and follow 'Launcher & wallpaper' in the README."
