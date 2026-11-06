#!/usr/bin/env bash
# Shared helpers for the Fitz-TV Shield toolkit.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
[ -f "$HERE/config.sh" ] && . "$HERE/config.sh"
SHIELD_IP="${SHIELD_IP:-}"
PRIVATE_DNS="${PRIVATE_DNS-dns.adguard-dns.com}"
TIERS="${TIERS:-core,bloat}"
ADB="${ADB:-adb}"
SNAP_ROOT="$HERE/snapshots"
BASELINE="$SNAP_ROOT/baseline"
TARGET=""

# Every setting any script in this toolkit may change. Their original values are
# saved in each snapshot so rollback.sh can put them back exactly.
TOUCHED_SETTINGS=(
  "global window_animation_scale"
  "global transition_animation_scale"
  "global animator_duration_scale"
  "global wifi_scan_always_enabled"
  "global ble_scan_always_enabled"
  "global private_dns_mode"
  "global private_dns_specifier"
)

# Packages that must never be disabled, whatever packages.txt says.
PROTECTED=(
  android com.android.systemui com.android.settings com.android.tv.settings
  com.android.providers.settings com.android.providers.tv com.android.vending
  com.google.android.gms com.google.android.gsf com.google.android.tvlauncher
  com.google.android.katniss com.google.android.apps.mediashell
  com.google.android.tv.remote.service com.google.android.tungsten.setupwraith
  com.android.packageinstaller com.google.android.packageinstaller
  com.android.inputmethod.latin com.google.android.leanback.ime
  com.nvidia.shield.remote.server com.nvidia.blakepairing com.nvidia.ota
  com.nvidia.shieldtech.accessoryserver com.nvidia.tegrazone3.updater
)

log()  { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }

adb_()  { "$ADB" -s "$TARGET" "$@"; }
sh_()   { "$ADB" -s "$TARGET" shell "$@" </dev/null | tr -d '\r'; }
pkgs()  { sh_ pm list packages "$@" | sed -n 's/^package://p' | sort -u; }

connect() {
  command -v "$ADB" >/dev/null 2>&1 || die "adb not found. Install Android platform-tools and put adb on your PATH."
  if [ -n "$SHIELD_IP" ]; then
    case "$SHIELD_IP" in *:*) TARGET="$SHIELD_IP" ;; *) TARGET="$SHIELD_IP:5555" ;; esac
    log "Connecting to $TARGET (accept the 'Allow network debugging' prompt on the TV the first time)"
    "$ADB" connect "$TARGET" >/dev/null 2>&1 || true
  else
    TARGET="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
    [ -n "$TARGET" ] || die "No device connected. Set SHIELD_IP in config.sh."
  fi
  local state=""
  for _ in 1 2 3 4 5; do
    state="$("$ADB" -s "$TARGET" get-state 2>/dev/null || true)"
    [ "$state" = device ] && break
    sleep 2
  done
  [ "$state" = device ] || die "Could not talk to $TARGET (state: ${state:-none}). Is Network debugging on and the prompt accepted?"
  local maker model
  maker="$(sh_ getprop ro.product.manufacturer)"; model="$(sh_ getprop ro.product.model)"
  log "Connected: $maker $model (Android $(sh_ getprop ro.build.version.release))"
  if [ "${maker,,}" != nvidia ] && [ "${FORCE:-0}" != 1 ]; then
    die "This doesn't look like an NVIDIA Shield. Re-run with FORCE=1 if you're sure."
  fi
}

# Packages that answer the HOME intent (launchers) — never touched.
launcher_pkgs() {
  sh_ cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
    | grep '/' | sed 's#/.*##; s/^ *//' | sort -u || true
}

take_snapshot() {   # $1 = destination dir
  local d="$1"; mkdir -p "$d"
  log "Saving snapshot to $d"
  pkgs -u > "$d/packages-all.txt"          # includes ones removed for user 0
  pkgs    > "$d/packages-installed.txt"
  pkgs -d > "$d/packages-disabled.txt"
  pkgs -3 > "$d/packages-user.txt"         # apps you installed
  launcher_pkgs > "$d/launchers.txt"
  sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
    | tail -n 1 > "$d/default-launcher.txt" || true
  for ns in global secure system; do sh_ settings list "$ns" > "$d/settings-$ns.txt" || true; done
  : > "$d/touched-settings.tsv"
  local entry ns key val
  for entry in "${TOUCHED_SETTINGS[@]}"; do
    ns="${entry% *}"; key="${entry#* }"
    val="$(sh_ settings get "$ns" "$key")"
    printf '%s\t%s\t%s\n' "$ns" "$key" "$val" >> "$d/touched-settings.tsv"
  done
  sh_ getprop > "$d/getprop.txt" || true
  sh_ dumpsys package packages 2>/dev/null | grep -E '^  Package \[|versionName=' > "$d/package-versions.txt" || true
  date '+%Y-%m-%d %H:%M:%S' > "$d/created.txt"
  echo "$TARGET" >> "$d/created.txt"
  log "Snapshot saved ($(wc -l < "$d/packages-installed.txt" | tr -d ' ') packages, $(wc -l < "$d/packages-user.txt" | tr -d ' ') user-installed)"
}

ensure_baseline() {
  if [ ! -f "$BASELINE/created.txt" ]; then
    log "No baseline snapshot yet — taking one before changing anything."
    take_snapshot "$BASELINE"
  else
    log "Baseline snapshot exists ($(head -n1 "$BASELINE/created.txt")) — rollback will return to it."
  fi
}

in_list() { local x="$1"; shift; local i; for i in "$@"; do [ "$i" = "$x" ] && return 0; done; return 1; }
