# Fitz-TV Shield toolkit configuration — edit these to match your setup.

# Your Shield's IP address (Settings > Device Preferences > About > Status, or Network).
# Leave empty to use whichever ADB device is already connected (e.g. over USB).
SHIELD_IP="192.168.50.239"

# Private DNS hostname used to block ads/telemetry network-wide on the Shield.
# Options: dns.adguard-dns.com (AdGuard, ads+trackers), family.adguard-dns.com,
# or your own NextDNS id like "abc123.dns.nextdns.io". Set to "" to skip.
PRIVATE_DNS="dns.adguard-dns.com"

# Which debloat tiers to apply (see packages.txt): core, bloat, optional
TIERS="core,bloat"
