#!/usr/bin/env bash
# One-click cleaner: clears every app's cache, closes background apps,
# reports RAM + storage before/after. Doesn't touch app data, logins or settings,
# and doesn't stop whatever is currently on screen.
. "$(dirname "$0")/lib.sh"
connect
mem()  { sh_ cat /proc/meminfo | awk '/MemAvailable/ {printf "%d", $2/1024}'; }
disk() { sh_ df /data | awk 'NR==2 {printf "%d", $4/1024}'; }
m0=$(mem); d0=$(disk)
log "Clearing all app caches";   sh_ pm trim-caches 9999G >/dev/null || warn "trim-caches failed"
log "Closing background apps";   sh_ am kill-all >/dev/null || true
sleep 2
m1=$(mem); d1=$(disk)
log "Free RAM:     ${m0} MB -> ${m1} MB"
log "Free storage: ${d0} MB -> ${d1} MB (+$((d1-d0)) MB)"
