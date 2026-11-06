#!/usr/bin/env bash
# Full setup in order: baseline snapshot -> debloat -> tweaks -> apps.
# Shows a preview first and asks before changing anything.
set -euo pipefail
cd "$(dirname "$0")"
bash snapshot.sh
bash debloat.sh
bash tweaks.sh
read -r -p "Apply the changes above? [y/N] " ok
[ "${ok,,}" = y ] || { echo "Nothing changed."; exit 0; }
bash debloat.sh --apply
bash tweaks.sh --apply --optimize
bash install-apps.sh
echo "All done. To undo everything: ./rollback.sh --apply"
