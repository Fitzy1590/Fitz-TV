#!/usr/bin/env bash
# Disable bloat/telemetry packages listed in packages.txt.
# Preview by default; pass --apply to make changes. --tiers core,bloat,optional overrides config.
. "$(dirname "$0")/lib.sh"
APPLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --apply) APPLY=1 ;;
    --tiers) TIERS="$2"; shift ;;
    *) die "Unknown option $1" ;;
  esac; shift
done
connect
[ "$APPLY" = 1 ] && ensure_baseline

mapfile -t INSTALLED < <(pkgs)
mapfile -t USERAPPS  < <(pkgs -3)
mapfile -t DISABLED  < <(pkgs -d)
mapfile -t LAUNCHERS < <(launcher_pkgs)
[ "${#LAUNCHERS[@]}" -gt 0 ] && log "Launchers protected: ${LAUNCHERS[*]}"

tier=""; n_do=0
while IFS= read -r raw || [ -n "$raw" ]; do
  line="${raw%%#*}"; line="$(echo "$line" | xargs)"
  [ -z "$line" ] && continue
  if [[ "$line" =~ ^\[(.+)\]$ ]]; then tier="${BASH_REMATCH[1]}"; continue; fi
  pkg="$line"
  [[ ",$TIERS," == *",$tier,"* ]] || continue
  if ! in_list "$pkg" "${INSTALLED[@]}";   then echo "  skip  $pkg (not on this device)"; continue; fi
  if in_list "$pkg" "${PROTECTED[@]}";     then echo "  skip  $pkg (protected)"; continue; fi
  if in_list "$pkg" "${USERAPPS[@]}";      then echo "  skip  $pkg (you installed it)"; continue; fi
  if [ "${#LAUNCHERS[@]}" -gt 0 ] && in_list "$pkg" "${LAUNCHERS[@]}"; then echo "  skip  $pkg (launcher)"; continue; fi
  if [ "${#DISABLED[@]}" -gt 0 ] && in_list "$pkg" "${DISABLED[@]}"; then echo "  ok    $pkg (already disabled)"; continue; fi
  n_do=$((n_do+1))
  if [ "$APPLY" = 1 ]; then
    out="$(sh_ pm disable-user --user 0 "$pkg" 2>&1 || true)"
    case "$out" in *"new state: disabled"*) echo "  DONE  $pkg [$tier]" ;; *) warn "could not disable $pkg: $out" ;; esac
    sh_ am force-stop "$pkg" >/dev/null 2>&1 || true
  else
    echo "  would disable  $pkg [$tier]"
  fi
done < "$HERE/packages.txt"

if [ "$APPLY" = 1 ]; then log "Debloat finished ($n_do changed). Undo any time with ./rollback.sh"
else log "Preview only — $n_do package(s) would be disabled. Re-run with --apply to do it."; fi
