#!/usr/bin/env bash
# Speed + privacy tweaks: animations off, background radio scans off,
# ad/tracker-blocking Private DNS, optional app pre-compilation.
# Preview by default; --apply to change. Options: --no-dns, --optimize
. "$(dirname "$0")/lib.sh"
APPLY=0; OPTIMIZE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --apply) APPLY=1 ;;
    --no-dns) PRIVATE_DNS="" ;;
    --optimize) OPTIMIZE=1 ;;
    *) die "Unknown option $1" ;;
  esac; shift
done
connect
[ "$APPLY" = 1 ] && ensure_baseline

set_() { # ns key value why
  local cur; cur="$(sh_ settings get "$1" "$2")"
  if [ "$cur" = "$3" ]; then echo "  ok    $1 $2 = $3"; return; fi
  if [ "$APPLY" = 1 ]; then sh_ settings put "$1" "$2" "$3"; echo "  DONE  $1 $2: $cur -> $3   ($4)"
  else echo "  would set  $1 $2: $cur -> $3   ($4)"; fi
}

log "Animations (instant menus)"
set_ global window_animation_scale     0 "no window open/close animation"
set_ global transition_animation_scale 0 "no screen transitions"
set_ global animator_duration_scale    0 "no UI animator effects"

log "Background work"
set_ global wifi_scan_always_enabled 0 "stop background Wi-Fi scanning for location"
set_ global ble_scan_always_enabled  0 "stop background Bluetooth scanning (remotes/controllers unaffected)"

if [ -n "$PRIVATE_DNS" ]; then
  log "Ads, telemetry & tracker blocking via Private DNS ($PRIVATE_DNS)"
  set_ global private_dns_mode      hostname       "encrypted DNS"
  set_ global private_dns_specifier "$PRIVATE_DNS" "blocklist DNS provider"
fi

if [ "$OPTIMIZE" = 1 ]; then
  if [ "$APPLY" = 1 ]; then
    log "Pre-compiling apps for faster launches (can take 5-15 min; safe to let it run)"
    sh_ cmd package bg-dexopt-job || warn "bg-dexopt-job not supported on this firmware"
  else echo "  would run  cmd package bg-dexopt-job (pre-compile apps)"; fi
fi

[ "$APPLY" = 1 ] && log "Tweaks applied. Undo with ./rollback.sh" || log "Preview only — re-run with --apply."
