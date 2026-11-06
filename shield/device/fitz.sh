#!/system/bin/sh
# Fitz-TV device-side engine. Runs ON the Shield via `adb shell`.
# The Fitz-TV app pushes this file plus packages.txt to $FITZ_DIR and runs:
#   sh fitz.sh status
#   sh fitz.sh snapshot
#   sh fitz.sh debloat  [apply] [tiers=core,bloat]
#   sh fitz.sh tweaks   [apply] [dns=<host>|dns=] [optimize]
#   sh fitz.sh rollback [apply]
#   sh fitz.sh clean
#   sh fitz.sh apps running|all|user        (ROW lines: pkg MB kind state verdict bg)
#   sh fitz.sh disable|enable|stop <pkg>   sh fitz.sh bg <pkg> on|off
#   sh fitz.sh health | playback | launchers | sethome <component> | stocklauncher on|off
#   sh fitz.sh maintain | lock
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
rec_add() { has "$1" "$2" || echo "$1" >> "$2"; }
rec_del() { [ -f "$2" ] || return 0; grep -vxF "$1" "$2" > "$2.n"; mv "$2.n" "$2"; }
DISREC="$D/fitz-disabled.txt"   # packages this app disabled (re-applied by nightly maintenance)
BGREC="$D/bg-restricted.txt"    # packages blocked from running in the background
home_default() {
  cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME </dev/null 2>/dev/null \
    | grep / | tail -n 1 | sed 's/^ *//'
}
sget() { settings get "$1" "$2" </dev/null 2>/dev/null; }
same() {  # equal as text, or as numbers ("0.0" == "0")
  [ "$1" = "$2" ] && return 0
  case "$1$2" in *[!0-9.]*) return 1 ;; esac
  awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 == b + 0) }'
}
optimize_state() {
  if tail -n 1 "$D/optimize.log" 2>/dev/null | grep -q '^finished'; then echo "done ($(tail -n 1 "$D/optimize.log"))"
  elif [ -f "$D/optimize.pid" ] && kill -0 "$(cat "$D/optimize.pid")" 2>/dev/null; then echo running
  elif [ -f "$D/optimize.log" ]; then tail -n 1 "$D/optimize.log"
  else echo "never run"; fi
}

snapshot_to() {
  mkdir -p "$1"
  pk -u > "$1/packages-all.txt"
  pk    > "$1/packages-installed.txt"
  pk -d > "$1/packages-disabled.txt"
  pk -3 > "$1/packages-user.txt"
  launchers > "$1/launchers.txt"
  home_default > "$1/home-default.txt"
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
  echo "App pre-compiling: $(optimize_state)"
  [ -f "$D/maintain.log" ] && echo "Last maintenance: $(grep '^===' "$D/maintain.log" | tail -n 1 | sed 's/=== //')"
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
      case "$out" in *"new state: disabled"*) echo "  DONE  $p [$tier]"; rec_add "$p" "$DISREC"; am force-stop "$p" </dev/null >/dev/null 2>&1 ;;
        *) echo "  FAIL  $p: $out" ;; esac
    else echo "  would disable  $p [$tier]"; fi
  done < "$D/packages.txt"
  if [ $apply = 1 ]; then echo "Debloat done: $n changed."; else echo "Preview: $n package(s) would be disabled."; fi
}

start_optimize() {
  if [ "$(optimize_state)" = running ]; then echo "App pre-compiling is already running in the background."; return; fi
  # Detached from this ADB session: keeps going even if the phone disconnects or the app closes.
  L="$D/optimize.log"
  setsid nohup sh -c "echo \"started \$(date '+%H:%M')\" > '$L'; cmd package bg-dexopt-job >> '$L' 2>&1; echo \"finished \$(date '+%H:%M')\" >> '$L'" </dev/null >/dev/null 2>&1 &
  echo $! > "$D/optimize.pid"
  echo "Pre-compiling apps in the background on the Shield (5-15 min)."
}

do_tweaks() {
  apply=0; dns="dns.adguard-dns.com"; opt=0
  for a in "$@"; do case "$a" in apply) apply=1 ;; dns=*) dns="${a#dns=}" ;; optimize) opt=1 ;; esac; done
  [ $apply = 1 ] && ensure_baseline
  sset() {
    cur="$(sget "$1" "$2")"
    if same "$cur" "$3"; then echo "  ok    $2 = $cur"
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
    if [ $apply = 0 ]; then echo "  would pre-compile apps (runs in the background on the Shield)"
    else start_optimize; echo "You can close this app; tap 'Connect & check status' later to see when it's finished."
    fi
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
    cur="$(sget "$ns" "$k")"; same "$cur" "$v" && continue
    if [ "$v" = null ]; then echo "  reset   $k ($cur -> default)"; [ $apply = 1 ] && settings delete "$ns" "$k" </dev/null >/dev/null 2>&1
    else echo "  set     $k ($cur -> $v)"; [ $apply = 1 ] && settings put "$ns" "$k" "$v" </dev/null; fi
  done < "$B/touched-settings.tsv"
  if [ -s "$BGREC" ]; then
    while read -r p; do
      echo "  allow background  $p"
      [ $apply = 1 ] && bg_set "$p" off >/dev/null
    done < "$BGREC"
  fi
  if [ -s "$B/home-default.txt" ]; then
    h="$(cat "$B/home-default.txt")"
    if [ "$(home_default)" != "$h" ]; then echo "  default launcher -> $h"; [ $apply = 1 ] && cmd package set-home-activity "$h" </dev/null >/dev/null 2>&1; fi
  fi
  [ $apply = 1 ] && rm -f "$DISREC" "$BGREC"
  added=""; while read -r p; do has "$p" "$B/packages-user.txt" || added="$added $p"; done < "$T/user"
  [ -n "$added" ] && echo "Apps installed since snapshot (left alone):$added"
  if [ $apply = 1 ]; then echo "Rollback complete. A reboot is recommended."; else echo "Preview only."; fi
}

# ---------- apps: RAM, verdicts, per-app actions ----------
TAB="$(printf '\t')"

# RAM per package (MB), from the "Total PSS/RSS by process" section of dumpsys meminfo.
mem_by_pkg() {
  dumpsys meminfo </dev/null 2>/dev/null | awk '
    /^Total (PSS|RSS) by process:/ { on = 1; next }
    on && /^[ \t]*$/ { exit }
    on {
      line = $0; sub(/^[ \t]*/, "", line)
      i = index(line, ":"); if (i == 0) next
      kb = substr(line, 1, i - 1); gsub(/[,K ]/, "", kb)
      rest = substr(line, i + 1); sub(/^[ \t]*/, "", rest)
      split(rest, b, " "); proc = b[1]; sub(/:.*/, "", proc)
      m[proc] += kb
    }
    END { for (p in m) printf "%s\t%d\n", p, m[p] / 1024 }'
}

# Map process names (e.g. com.google.android.gms.persistent) to installed packages.
mem_table() {   # writes "$T/mem": pkg<TAB>MB, sorted by MB desc
  pk -u > "$T/allp"
  mem_by_pkg | while IFS="$TAB" read -r proc mb; do
    p="$proc"; n=0
    while ! has "$p" "$T/allp" && [ $n -lt 3 ]; do
      case "$p" in *.*) p="${p%.*}" ;; *) break ;; esac; n=$((n+1))
    done
    has "$p" "$T/allp" || p="$proc"
    printf '%s\t%s\n' "$p" "$mb"
  done | awk -F'\t' '{ m[$1] += $2 } END { for (p in m) printf "%s\t%d\n", p, m[p] }' | sort -t "$TAB" -k2,2nr > "$T/mem"
}

prep_lists() {
  pk > "$T/inst"; pk -3 > "$T/user"; pk -d > "$T/dis"; pk -s > "$T/sys"; launchers > "$T/home"
  printf '%s\n' $PROTECTED > "$T/prot"
  sed 's/#.*//; s/\[.*\]//' "$D/packages.txt" | tr -s ' \t' '\n\n' | grep . > "$T/rec"
  touch "$BGREC"
}

verdict() {
  p="$1"
  if has "$p" "$T/prot"; then echo keep; return; fi
  if has "$p" "$T/home"; then echo launcher; return; fi
  if has "$p" "$T/user"; then echo yours; return; fi
  if has "$p" "$T/rec";  then echo safe; return; fi
  case "$p" in
    android|system|*overlay*|com.android.systemui|com.android.providers.*|com.android.server.*|com.android.networkstack*|\
    com.google.android.networkstack*|com.android.bluetooth*|com.android.se|com.android.shell|com.android.phone|\
    com.google.android.gms*|com.google.android.gsf*|com.nvidia.shield*|com.nvidia.tegra*|com.android.inputdevices|\
    com.android.keychain|com.android.externalstorage|*permissioncontroller*|com.android.wifi*|com.google.android.ext.*|\
    com.android.location*|com.android.media*|com.google.android.media*|com.android.connectivity*|com.android.tethering|\
    com.android.certinstaller|com.android.statementservice|com.android.localtransport|com.android.backupconfirm|\
    com.android.sharedstoragebackup|com.android.proxyhandler|com.android.vpndialogs|*webview*|com.google.android.tvsetup*|\
    com.google.android.tv.*|com.google.android.speech.pumpkin|com.google.android.tts|com.google.android.configupdater|\
    com.android.documentsui*|com.google.android.documentsui|com.android.dynsystem|com.android.nfc|com.android.carrierconfig|\
    com.google.android.syncadapters.*|com.android.providers*|com.android.packageinstaller|*setupwizard*|*setupwraith*|\
    com.android.wallpaperbackup|com.android.settings*|com.android.tv.settings|com.android.vending|com.android.cts*|\
    com.nvidia.ota|com.nvidia.blakepairing|com.nvidia.nvhw*|com.nvidia.diag*|com.nvidia.pcms*)
      echo keep ;;
    *) echo caution ;;
  esac
}

row() {   # row <pkg> <MB>
  p="$1"; mb="${2:-0}"
  if has "$p" "$T/user"; then kind=user; else kind=system; fi
  if has "$p" "$T/dis"; then st=disabled; elif has "$p" "$T/inst"; then st=enabled; else st=removed; fi
  if has "$p" "$BGREC"; then bg=blocked; else bg=allowed; fi
  printf 'ROW\t%s\t%s\t%s\t%s\t%s\t%s\n' "$p" "$mb" "$kind" "$st" "$(verdict "$p")" "$bg"
}

do_apps() {
  mode="${1:-running}"
  prep_lists; mem_table
  case "$mode" in
    running) while IFS="$TAB" read -r p mb; do has "$p" "$T/inst" && row "$p" "$mb"; done < "$T/mem" ;;
    all|user)
      if [ "$mode" = all ]; then src="$T/sys"; else src="$T/user"; fi
      while read -r p; do
        mb="$(grep "^$p$TAB" "$T/mem" | cut -f2)"; row "$p" "${mb:-0}"
      done < "$src" | sort -t "$TAB" -k3,3nr ;;
  esac
  echo "MEM$TAB$(awk '/MemTotal/{t=$2}/MemAvailable/{a=$2}END{printf "%d\t%d", a/1024, t/1024}' /proc/meminfo)"
}

guard() {   # refuse to touch core packages
  prep_lists
  v="$(verdict "$1")"
  case "$v" in keep|launcher) echo "REFUSED: $1 is a core $v package and is never disabled here."; return 1 ;; esac
  return 0
}

do_disable() {
  p="$1"; [ -n "$p" ] || return 2
  guard "$p" || return 1
  ensure_baseline >/dev/null
  out="$(pm disable-user --user 0 "$p" </dev/null 2>&1)"
  case "$out" in *"new state: disabled"*) rec_add "$p" "$DISREC"; am force-stop "$p" </dev/null >/dev/null 2>&1; echo "Disabled $p";;
    *) echo "FAILED: $out"; return 1 ;; esac
}
do_enable() {
  p="$1"; [ -n "$p" ] || return 2
  out="$(pm enable --user 0 "$p" </dev/null 2>&1)"
  rec_del "$p" "$DISREC"
  case "$out" in *"new state: enabled"*|*"new state: default"*) echo "Enabled $p" ;;
    *) # maybe removed for user 0: bring it back
       if cmd package install-existing "$p" </dev/null 2>&1 | grep -qi installed; then echo "Restored $p"; else echo "FAILED: $out"; return 1; fi ;;
  esac
}
bg_set() {   # bg_set <pkg> on|off   (on = block background running)
  if [ "$2" = on ]; then
    cmd appops set "$1" RUN_ANY_IN_BACKGROUND ignore </dev/null 2>/dev/null
    cmd appops set "$1" RUN_IN_BACKGROUND ignore </dev/null 2>/dev/null
    rec_add "$1" "$BGREC"; echo "Background running blocked for $1"
  else
    cmd appops set "$1" RUN_ANY_IN_BACKGROUND default </dev/null 2>/dev/null
    cmd appops set "$1" RUN_IN_BACKGROUND default </dev/null 2>/dev/null
    rec_del "$1" "$BGREC"; echo "Background running allowed for $1"
  fi
}
do_bg() {
  p="$1"; [ -n "$p" ] || return 2
  if [ "$2" = on ]; then guard "$p" || return 1; ensure_baseline >/dev/null; am force-stop "$p" </dev/null >/dev/null 2>&1; fi
  bg_set "$p" "$2"
}

# ---------- health & playback ----------
kv() { printf 'KV\t%s\t%s\n' "$1" "$2"; }
do_health() {
  kv "RAM free" "$(awk '/MemTotal/{t=$2}/MemAvailable/{a=$2}END{printf "%d MB of %d MB (%d%%)", a/1024, t/1024, a*100/t}' /proc/meminfo)"
  sf="$(df -h /data 2>/dev/null | awk 'NR==2{print $4" of "$2}')"; [ -n "$sf" ] && kv "Storage free" "$sf"
  kv "CPU load" "$(cut -d' ' -f1-3 /proc/loadavg) (1/5/15 min, $(grep -c '^processor' /proc/cpuinfo) cores)"
  f=/sys/devices/system/cpu/cpu0/cpufreq
  if [ -r $f/scaling_cur_freq ]; then
    kv "CPU speed" "$(( $(cat $f/scaling_cur_freq) / 1000 )) MHz of $(( $(cat $f/cpuinfo_max_freq) / 1000 )) MHz max"
  fi
  for z in $(ls /sys/class/thermal 2>/dev/null | grep thermal_zone); do
    t="$(cat /sys/class/thermal/$z/temp 2>/dev/null)"; ty="$(cat /sys/class/thermal/$z/type 2>/dev/null)"
    case "$t" in ''|*[!0-9-]*) continue ;; esac
    [ "$t" -gt 1000 ] && t=$((t/1000))
    [ "$t" -gt 0 ] && [ "$t" -lt 150 ] && kv "Temp $ty" "${t} °C"
  done
  up="$(cut -d. -f1 /proc/uptime)"; kv "Uptime" "$((up/86400))d $((up%86400/3600))h $((up%3600/60))m"
  if [ "$(cat /sys/class/net/eth0/operstate 2>/dev/null)" = up ]; then
    sp="$(cat /sys/class/net/eth0/speed 2>/dev/null)"
    case "$sp" in ''|-*|0) kv "Network" "Ethernet, connected" ;; *) kv "Network" "Ethernet, $sp Mbps link" ;; esac
  else
    w="$(dumpsys wifi </dev/null 2>/dev/null | grep -m1 'mWifiInfo')"
    rssi="$(echo "$w" | sed -n 's/.*RSSI: \(-*[0-9]*\).*/\1/p')"
    ls="$(echo "$w" | sed -n 's/.*Link speed: \([0-9]*Mbps\).*/\1/p')"
    fq="$(echo "$w" | sed -n 's/.*Frequency: \([0-9]*MHz\).*/\1/p')"
    kv "Network" "Wi-Fi, signal ${rssi:-?} dBm, link ${ls:-?}, ${fq:-?}"
  fi
  top -b -n 1 -m 6 -o %CPU,RES,NAME 2>/dev/null </dev/null | grep -vE '^ *$|Tasks|Mem|Swap|CPU|%cpu|top -b' | head -n 6 | while read -r c r n; do
    kv "Top: $n" "CPU $c%, RAM $r"
  done
}

do_playback() {
  fg="$(dumpsys activity activities </dev/null 2>/dev/null | grep -m1 -E 'mResumedActivity|ResumedActivity' | sed 's/.* \([a-zA-Z0-9_.]*\/[^ }]*\).*/\1/')"
  kv "On screen" "${fg:-unknown}"
  dumpsys media_session </dev/null 2>/dev/null | awk '
    /package=/ { split($0, a, "package="); split(a[2], b, " "); pkg = b[1] }
    /state=PlaybackState/ { s = $0; sub(/.*state=PlaybackState \{state=/, "", s); sub(/,.*/, "", s)
      st = (s == 3 ? "playing" : s == 2 ? "paused" : s == 6 ? "buffering" : s == 1 ? "stopped" : "state " s)
      if (pkg != "") printf "KV\tMedia: %s\t%s\n", pkg, st; pkg = "" }' | head -n 4
  di="$(dumpsys display </dev/null 2>/dev/null | grep -m1 -E 'mOverrideDisplayInfo|mBaseDisplayInfo|DisplayInfo\{')"
  res="$(echo "$di" | sed -n 's/.*real \([0-9]* x [0-9]*\).*/\1/p')"
  fps="$(echo "$di" | sed -n 's/.*, \([0-9.]*\) fps.*/\1/p')"
  [ -z "$fps" ] && fps="$(echo "$di" | sed -n 's/.*refreshRate \([0-9.]*\).*/\1/p')"
  kv "Video output" "${res:-?} @ ${fps:-?} Hz"
  hdr="$(echo "$di" | sed -n 's/.*supportedHdrTypes=\[\([^]]*\)\].*/\1/p')"
  [ -n "$hdr" ] && kv "TV HDR support (types)" "$hdr  (1=Dolby Vision 2=HDR10 3=HLG 4=HDR10+)"
  cm="$(dumpsys SurfaceFlinger </dev/null 2>/dev/null | grep -m1 -iE 'Current color mode|colorMode')"
  [ -n "$cm" ] && kv "Colour mode" "$(echo "$cm" | sed 's/^ *//')"
  dumpsys media.audio_flinger </dev/null 2>/dev/null | awk '
    /Output thread/ { t = 1 }
    t && /Standby: no/ { active = 1 }
    t && /HAL format:|^  Format:/ { f = $0; sub(/^ */, "", f) }
    t && /Sample rate:/ { r = $0; sub(/^ */, "", r) }
    t && /Output device|Output devices/ { d = $0; sub(/^ */, "", d)
      if (active) { printf "KV\tAudio out\t%s | %s | %s\n", f, r, d; n++ }
      t = 0; active = 0 }
    END { if (!n) print "KV\tAudio out\tidle (nothing playing)" }' | head -n 3
}

# ---------- launchers ----------
do_launchers() {
  cur="$(home_default)"
  cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME </dev/null 2>/dev/null \
    | grep / | sed 's/^ *//' | while read -r c; do
      p="${c%%/*}"; d=no; [ "$c" = "$cur" ] && d=yes
      printf 'ROW\t%s\t%s\t%s\n' "$c" "$p" "$d"
    done
  pk -d > "$T/dis"
  for p in com.google.android.tvlauncher com.google.android.tvrecommendations; do
    if has "$p" "$T/dis"; then printf 'STOCK\t%s\tdisabled\n' "$p"; else printf 'STOCK\t%s\tenabled\n' "$p"; fi
  done
}
remember_home() {   # baselines made by older versions lack the default launcher: record it before changing it
  ensure_baseline >/dev/null
  [ -s "$B/home-default.txt" ] || home_default > "$B/home-default.txt"
}
do_sethome() {
  [ -n "$1" ] || return 2
  remember_home
  cmd package set-home-activity "$1" </dev/null 2>&1 | grep -v '^$'
  echo "Default launcher: $(home_default)"
}
do_stocklauncher() {
  remember_home
  if [ "$1" = off ]; then
    others="$(launchers | grep -vxE 'com.google.android.tvlauncher|com.android.tv.settings' | head -n 1)"
    [ -n "$others" ] || { echo "REFUSED: install another launcher first (Projectivy) so you're never left without a home screen."; return 1; }
    for p in com.google.android.tvlauncher com.google.android.tvrecommendations; do
      pk > "$T/inst"; has "$p" "$T/inst" || continue
      pm disable-user --user 0 "$p" </dev/null >/dev/null 2>&1 && rec_add "$p" "$DISREC" && echo "Disabled $p"
    done
    echo "Stock launcher off. Press Home: your other launcher takes over."
  else
    for p in com.google.android.tvlauncher com.google.android.tvrecommendations; do
      pm enable --user 0 "$p" </dev/null >/dev/null 2>&1; rec_del "$p" "$DISREC"; echo "Enabled $p"
    done
  fi
}

# ---------- maintenance & lock ----------
do_maintain() {
  L="$D/maintain.log"
  { echo "=== $(date '+%Y-%m-%d %H:%M') maintenance"
    pm trim-caches 9999G </dev/null >/dev/null 2>&1 && echo "caches cleared"
    if [ -s "$DISREC" ]; then
      pk -d > "$T/dis"; pk > "$T/inst"
      while read -r p; do
        has "$p" "$T/inst" || continue; has "$p" "$T/dis" && continue
        pm disable-user --user 0 "$p" </dev/null >/dev/null 2>&1 && echo "re-disabled $p (an update had re-enabled it)"
      done < "$DISREC"
    fi
    if [ -s "$BGREC" ]; then while read -r p; do bg_set "$p" on >/dev/null; done < "$BGREC"; echo "background blocks re-applied"; fi
  } >> "$L" 2>&1
  tail -n 20 "$L" > "$L.n" 2>/dev/null; mv "$L.n" "$L"
  cat "$L" | sed -n '/^===/h; /^===/!H; ${x;p;}'
  start_optimize
}
do_lock() {
  echo "Turning off USB/Network debugging in 2 seconds. This connection will drop."
  echo "To turn it back on: Settings > Device Preferences > Developer options > USB debugging + Network debugging."
  setsid nohup sh -c 'sleep 2; settings put global adb_enabled 0' </dev/null >/dev/null 2>&1 &
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
  apps) do_apps "$@" ;;
  disable) do_disable "$1" ;;
  enable) do_enable "$1" ;;
  stop) am force-stop "$1" </dev/null && echo "Stopped $1" ;;
  bg) do_bg "$1" "$2" ;;
  health) do_health ;;
  playback) do_playback ;;
  launchers) do_launchers ;;
  sethome) do_sethome "$1" ;;
  stocklauncher) do_stocklauncher "$1" ;;
  maintain) do_maintain ;;
  lock) do_lock ;;
  *) echo "usage: fitz.sh status|snapshot|debloat|tweaks|rollback|clean [apply] ..."; exit 2 ;;
esac
