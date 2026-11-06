#!/system/bin/sh
# Fitz-TV device-side engine. Runs ON the Shield via `adb shell`.
# The Fitz-TV app pushes this file plus packages.txt to $FITZ_DIR and runs:
#   sh fitz.sh status
#   sh fitz.sh snapshot
#   sh fitz.sh debloat  [apply] [tiers=core,bloat]
#   sh fitz.sh tweaks   [apply] [dns=<host>|dns=] [optimize]
#   sh fitz.sh rollback [apply]
#   sh fitz.sh clean
# Everything is reversible; nothing is ever uninstalled.

set -f   # no globbing: package-list lines like [core] must stay literal
D="${FITZ_DIR:-/data/local/tmp/fitztv}"
B="$D/baseline"
T="$D/tmp"; mkdir -p "$T"

SETTINGS="global:window_animation_scale global:transition_animation_scale global:animator_duration_scale
global:wifi_scan_always_enabled global:ble_scan_always_enabled global:private_dns_mode global:private_dns_specifier"

PROTECTED="android com.android.systemui com.android.settings com.android.tv.settings
com.android.providers.settings com.android.providers.tv com.android.vending
com.google.android.gms com.google.android.gsf com.google.android.tvlauncher
com.google.android.katniss com.google.android.apps.mediashell
com.google.android.tv.remote.service com.google.android.tungsten.setupwraith
com.android.packageinstaller com.google.android.packageinstaller
com.android.inputmethod.latin com.google.android.leanback.ime
com.nvidia.shield.remote.server com.nvidia.blakepairing com.nvidia.ota
com.nvidia.shieldtech.accessoryserver com.nvidia.tegrazone3.updater"

pk() { pm list packages "$@" </dev/null 2>/dev/null | sed -n 's/^package://p' | sort -u; }
launchers() {
  cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME </dev/null 2>/dev/null \
    | grep / | sed 's#/.*##; s/^ *//' | sort -u
}
has() { grep -qxF "$1" "$2" 2>/dev/null; }   # has <line> <file>
sget() { settings get "$1" "$2" </dev/null 2>/dev/null; }

snapshot_to() {
  mkdir -p "$1"
  pk -u > "$1/packages-all.txt"
  pk    > "$1/packages-installed.txt"
  pk -d > "$1/packages-disabled.txt"
  pk -3 > "$1/packages-user.txt"
  launchers > "$1/launchers.txt"
  : > "$1/touched-settings.tsv"
  for s in $SETTINGS; do
    ns="${s%%:*}"; k="${s#*:}"
    printf '%s\t%s\t%s\n' "$ns" "$k" "$(sget "$ns" "$k")" >> "$1/touched-settings.tsv"
  done
  for ns in global secure system; do settings list "$ns" </dev/null > "$1/settings-$ns.txt" 2>/dev/null; done
  date '+%Y-%m-%d %H:%M:%S' > "$1/created.txt"
  echo "Snapshot saved: $(wc -l < "$1/packages-installed.txt" | tr -d ' ') packages, $(wc -l < "$1/packages-user.txt" | tr -d ' ') installed by you"
}

ensure_baseline() {
  if [ -f "$B/created.txt" ]; then echo "Baseline snapshot: $(cat "$B/created.txt") (rollback returns here)"
  else echo "Taking baseline snapshot before any change..."; snapshot_to "$B"; fi
}

do_status() {
  echo "Device: $(getprop ro.product.manufacturer) $(getprop ro.product.model), Android $(getprop ro.build.version.release)"
  if [ -f "$B/created.txt" ]; then echo "Baseline snapshot: $(cat "$B/created.txt")"; else echo "Baseline snapshot: none yet"; fi
  echo "Disabled packages: $(pk -d | wc -l | tr -d ' ')"
  echo "Animations: $(sget global window_animation_scale)/$(sget global transition_animation_scale)/$(sget global animator_duration_scale)"
  echo "Private DNS: $(sget global private_dns_mode) $(sget global private_dns_specifier)"
}

do_debloat() {
  apply=0; tiers="core,bloat"
  for a in "$@"; do case "$a" in apply) apply=1 ;; tiers=*) tiers="${a#tiers=}" ;; esac; done
  [ $apply = 1 ] && ensure_baseline
  pk    > "$T/inst"; pk -3 > "$T/user"; pk -d > "$T/dis"; launchers > "$T/home"
  printf '%s\n' $PROTECTED > "$T/prot"
  echo "Launchers protected: $(tr '\n' ' ' < "$T/home")"
  tier=""; n=0
  while IFS= read -r raw || [ -n "$raw" ]; do
    line="${raw%%#*}"; set -- $line; p="$1"
    [ -z "$p" ] && continue
    case "$p" in \[*\]) tier="${p#[}"; tier="${tier%]}"; continue ;; esac
    case ",$tiers," in *",$tier,"*) ;; *) continue ;; esac
    if ! has "$p" "$T/inst"; then echo "  skip  $p (not on this device)"; continue; fi
    if has "$p" "$T/prot"; then echo "  skip  $p (protected)"; continue; fi
    if has "$p" "$T/user"; then echo "  skip  $p (you installed it)"; continue; fi
    if has "$p" "$T/home"; then echo "  skip  $p (launcher)"; continue; fi
    if has "$p" "$T/dis";  then echo "  ok    $p (already disabled)"; continue; fi
    n=$((n+1))
    if [ $apply = 1 ]; then
      out="$(pm disable-user --user 0 "$p" </dev/null 2>&1)"
      case "$out" in *"new state: disabled"*) echo "  DONE  $p [$tier]"; am force-stop "$p" </dev/null >/dev/null 2>&1 ;;
        *) echo "  FAIL  $p: $out" ;; esac
    else echo "  would disable  $p [$tier]"; fi
  done < "$D/packages.txt"
  if [ $apply = 1 ]; then echo "Debloat done: $n changed."; else echo "Preview: $n package(s) would be disabled."; fi
}

do_tweaks() {
  apply=0; dns="dns.adguard-dns.com"; opt=0
  for a in "$@"; do case "$a" in apply) apply=1 ;; dns=*) dns="${a#dns=}" ;; optimize) opt=1 ;; esac; done
  [ $apply = 1 ] && ensure_baseline
  sset() {
    cur="$(sget "$1" "$2")"
    if [ "$cur" = "$3" ]; then echo "  ok    $2 = $3"
    elif [ $apply = 1 ]; then settings put "$1" "$2" "$3" </dev/null; echo "  DONE  $2: $cur -> $3 ($4)"
    else echo "  would set  $2: $cur -> $3 ($4)"; fi
  }
  echo "Animations off (instant menus)"
  sset global window_animation_scale 0 "window animations"
  sset global transition_animation_scale 0 "screen transitions"
  sset global animator_duration_scale 0 "UI animator effects"
  echo "Background work"
  sset global wifi_scan_always_enabled 0 "background Wi-Fi scanning"
  sset global ble_scan_always_enabled 0 "background Bluetooth scanning"
  if [ -n "$dns" ]; then
    echo "Ad/tracker blocking DNS ($dns)"
    sset global private_dns_mode hostname "encrypted DNS"
    sset global private_dns_specifier "$dns" "blocklist provider"
  fi
  if [ $opt = 1 ]; then
    if [ $apply = 1 ]; then echo "Pre-compiling apps (5-15 min)..."; cmd package bg-dexopt-job </dev/null 2>&1 | tail -n 3
    else echo "  would pre-compile apps"; fi
  fi
  if [ $apply = 1 ]; then echo "Tweaks applied."; else echo "Preview only."; fi
}

do_rollback() {
  apply=0; [ "$1" = apply ] && apply=1
  [ -f "$B/created.txt" ] || { echo "No baseline snapshot — nothing to roll back to."; return 1; }
  echo "Rolling back to snapshot from $(cat "$B/created.txt")"
  pk -d > "$T/dis"; pk > "$T/inst"; pk -u > "$T/all"; pk -3 > "$T/user"
  while read -r p; do
    has "$p" "$B/packages-disabled.txt" && continue
    has "$p" "$B/packages-installed.txt" || continue
    echo "  enable  $p"; [ $apply = 1 ] && pm enable --user 0 "$p" </dev/null >/dev/null 2>&1
  done < "$T/dis"
  while read -r p; do
    has "$p" "$T/inst" && continue
    if has "$p" "$T/all"; then echo "  restore $p"; [ $apply = 1 ] && cmd package install-existing "$p" </dev/null >/dev/null 2>&1
    else echo "  !! $p was uninstalled — reinstall from Play Store"; fi
  done < "$B/packages-installed.txt"
  tab="$(printf '\t')"
  while IFS="$tab" read -r ns k v; do
    cur="$(sget "$ns" "$k")"; [ "$cur" = "$v" ] && continue
    if [ "$v" = null ]; then echo "  reset   $k ($cur -> default)"; [ $apply = 1 ] && settings delete "$ns" "$k" </dev/null >/dev/null 2>&1
    else echo "  set     $k ($cur -> $v)"; [ $apply = 1 ] && settings put "$ns" "$k" "$v" </dev/null; fi
  done < "$B/touched-settings.tsv"
  added=""; while read -r p; do has "$p" "$B/packages-user.txt" || added="$added $p"; done < "$T/user"
  [ -n "$added" ] && echo "Apps installed since snapshot (left alone):$added"
  if [ $apply = 1 ]; then echo "Rollback complete. A reboot is recommended."; else echo "Preview only."; fi
}

memfree()  { awk '/MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo; }
diskfree() { df /data | awk 'NR==2 {printf "%d", $4/1024}'; }
do_clean() {
  m0=$(memfree); d0=$(diskfree)
  echo "Clearing all app caches..."; pm trim-caches 9999G </dev/null >/dev/null 2>&1
  echo "Closing background apps...";  am kill-all </dev/null >/dev/null 2>&1
  sleep 2
  m1=$(memfree); d1=$(diskfree)
  echo "Free RAM:     $m0 MB -> $m1 MB"
  echo "Free storage: $d0 MB -> $d1 MB (+$((d1-d0)) MB)"
}

cmd_="$1"; [ $# -gt 0 ] && shift
case "$cmd_" in
  status) do_status ;;
  snapshot) if [ -f "$B/created.txt" ]; then snapshot_to "$D/snap-$(date +%Y%m%d-%H%M%S)"; else snapshot_to "$B"; fi ;;
  debloat) do_debloat "$@" ;;
  tweaks) do_tweaks "$@" ;;
  rollback) do_rollback "$@" ;;
  clean) do_clean ;;
  *) echo "usage: fitz.sh status|snapshot|debloat|tweaks|rollback|clean [apply] ..."; exit 2 ;;
esac
