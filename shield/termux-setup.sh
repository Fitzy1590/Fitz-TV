#!/usr/bin/env bash
# Run once inside Termux on your Android phone/tablet. Installs adb and adds
# one-tap home-screen shortcuts (needs the Termux:Widget app).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
pkg install -y android-tools
mkdir -p "$HOME/.shortcuts"
mk() { printf '#!/data/data/com.termux/files/usr/bin/bash\nbash "%s/%s" %s\necho; read -r -p "Press Enter to close" _\n' "$DIR" "$2" "${3:-}" > "$HOME/.shortcuts/$1"; chmod +x "$HOME/.shortcuts/$1"; }
mk "Shield Clean"    clean.sh
mk "Shield Rollback" rollback.sh --apply
chmod +x "$DIR"/*.sh
echo
echo "Done. Add the Termux:Widget widget to your home screen for one-tap 'Shield Clean'."
echo "Now run:  bash run-all.sh"
