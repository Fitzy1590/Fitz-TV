#!/usr/bin/env bash
# Full setup in order: baseline snapshot -> debloat -> tweaks -> apps.
# Shows a preview first and asks before changing anything.
set -euo pipefail
cd "$(dirname "$0")"
./snapshot.sh
./debloat.sh
./tweaks.sh
read -r -p "Apply the changes above? [y/N] " ok
[ "${ok,,}" = y ] || { echo "Nothing changed."; exit 0; }
./debloat.sh --apply
./tweaks.sh --apply --optimize
./install-apps.sh
echo "All done. To undo everything: ./rollback.sh --apply"
