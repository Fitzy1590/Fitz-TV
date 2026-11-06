#!/usr/bin/env bash
# Take a snapshot of package + settings state. The first one becomes the
# permanent "baseline"; later ones are timestamped.
. "$(dirname "$0")/lib.sh"
connect
if [ -f "$BASELINE/created.txt" ]; then
  take_snapshot "$SNAP_ROOT/$(date +%Y%m%d-%H%M%S)"
else
  take_snapshot "$BASELINE"
fi
