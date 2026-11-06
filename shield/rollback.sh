#!/usr/bin/env bash
# Return the Shield to a snapshot (default: baseline).
#   ./rollback.sh                 preview restoring baseline
#   ./rollback.sh --apply         restore baseline
#   ./rollback.sh --apply snapshots/20261106-120000
# Re-enables anything disabled since the snapshot, re-installs any system app
# removed since, and restores every setting this toolkit changes.
# It never uninstalls apps you've added since.
. "$(dirname "$0")/lib.sh"
APPLY=0; SNAP="$BASELINE"
while [ $# -gt 0 ]; do
  case "$1" in --apply) APPLY=1 ;; *) SNAP="$1" ;; esac; shift
done
[ -f "$SNAP/created.txt" ] || die "Snapshot not found: $SNAP"
connect
log "Rolling back to snapshot from $(head -n1 "$SNAP/created.txt")"
run() { if [ "$APPLY" = 1 ]; then sh_ "$@" >/dev/null; else echo "    would run: $*"; fi; }

# 1) packages disabled now but enabled in the snapshot -> enable
comm -13 "$SNAP/packages-disabled.txt" <(pkgs -d) | while read -r p; do
  grep -qx "$p" "$SNAP/packages-installed.txt" || continue
  echo "  enable  $p"; run pm enable --user 0 "$p"
done
# 2) installed in snapshot but now removed for this user -> restore
comm -23 "$SNAP/packages-installed.txt" <(pkgs) | while read -r p; do
  if pkgs -u | grep -qx "$p"; then echo "  restore $p"; run cmd package install-existing "$p"
  else warn "$p was fully uninstalled — reinstall it from the Play Store"; fi
done
# 3) settings
while IFS=$'\t' read -r ns key val; do
  cur="$(sh_ settings get "$ns" "$key")"
  [ "$cur" = "$val" ] && continue
  if [ "$val" = null ]; then echo "  reset   $ns $key ($cur -> default)"; run settings delete "$ns" "$key"
  else echo "  set     $ns $key ($cur -> $val)"; run settings put "$ns" "$key" "$val"; fi
done < "$SNAP/touched-settings.tsv"
# 4) report (not undo) apps added since the snapshot
added="$(comm -13 "$SNAP/packages-user.txt" <(pkgs -3))"
[ -n "$added" ] && { log "Apps installed since the snapshot (left in place — uninstall manually if wanted):"; echo "$added" | sed 's/^/    /'; }

if [ "$APPLY" = 1 ]; then log "Rollback complete. Reboot the Shield to be thorough: adb reboot"
else log "Preview only — re-run with --apply."; fi
