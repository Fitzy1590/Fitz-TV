#!/usr/bin/env python3
"""
fitztv.py - find and control a Samsung (Tizen) smart TV on your local Wi-Fi.

Run this on a computer connected to the SAME network as the TV.

  python3 fitztv.py discover            # scan the local network for Samsung TVs
  python3 fitztv.py info   [--ip IP]    # model, firmware year, developer mode, etc.
  python3 fitztv.py pair   [--ip IP]    # accept the prompt on the TV; token is saved
  python3 fitztv.py apps   [--ip IP]    # list installed apps, flag the usual bloat
  python3 fitztv.py launch APP_ID       # open an app
  python3 fitztv.py key KEY_HOME ...    # send remote-control buttons
  python3 fitztv.py checklist           # privacy / ad settings to switch off on the TV

Nothing here changes or deletes anything on the TV: Samsung's network API does
not allow uninstalling apps. Use the `apps` report to know what to delete from
the TV's own Apps > Settings screen.

Requires: pip install websocket-client
"""

import argparse
import base64
import concurrent.futures
import ipaddress
import json
import os
import socket
import ssl
import sys
import time
import urllib.request

CONFIG_PATH = os.path.expanduser("~/.fitztv.json")
CLIENT_NAME = "FitzTV"
REST_PORT = 8001
WS_PORT = 8002

# Pre-installed / promotional apps commonly treated as bloat on Samsung TVs.
# Matched case-insensitively against the app name. System apps among these
# usually cannot be deleted, only moved off the home row.
BLOAT_HINTS = [
    "samsung tv plus", "gaming hub", "samsung health", "art store",
    "samsung promotion", "e-manual", "universal guide", "smartthings",
    "bixby", "alexa", "rakuten", "pluto", "tubi", "facebook",
    "instagram", "tiktok", "deezer", "workout", "kids", "shop", "promo",
]


# --------------------------------------------------------------------------- config

def load_config():
    try:
        with open(CONFIG_PATH) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def save_config(cfg):
    with open(CONFIG_PATH, "w") as f:
        json.dump(cfg, f, indent=2)
    try:
        os.chmod(CONFIG_PATH, 0o600)
    except OSError:
        pass


def resolve_ip(args):
    ip = getattr(args, "ip", None) or load_config().get("ip")
    if not ip:
        sys.exit("No TV IP known yet. Run `discover` first or pass --ip.")
    return ip


# --------------------------------------------------------------------------- REST

def get_device_info(ip, timeout=2.0):
    """GET http://IP:8001/api/v2/ - returns the TV's self-description or None."""
    try:
        with urllib.request.urlopen(f"http://{ip}:{REST_PORT}/api/v2/", timeout=timeout) as r:
            data = json.loads(r.read().decode("utf-8", "replace"))
    except Exception:
        return None
    if not isinstance(data, dict) or "device" not in data:
        return None
    return data


def local_ipv4():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("192.0.2.1", 80))  # no packet is sent; just picks the LAN interface
        return s.getsockname()[0]
    except OSError:
        return None
    finally:
        s.close()


def ssdp_candidates(timeout=3.0):
    """Ask the network who is a Samsung TV via SSDP (UPnP multicast)."""
    msg = (
        "M-SEARCH * HTTP/1.1\r\n"
        "HOST: 239.255.255.250:1900\r\n"
        'MAN: "ssdp:discover"\r\n'
        "MX: 2\r\n"
        "ST: urn:samsung.com:device:RemoteControlReceiver:1\r\n\r\n"
    ).encode()
    found = set()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.settimeout(0.5)
    try:
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
        sock.sendto(msg, ("239.255.255.250", 1900))
        end = time.time() + timeout
        while time.time() < end:
            try:
                _, addr = sock.recvfrom(4096)
                found.add(addr[0])
            except socket.timeout:
                continue
    except OSError:
        pass
    finally:
        sock.close()
    return found


def discover(subnet=None):
    candidates = ssdp_candidates()
    if subnet is None:
        me = local_ipv4()
        if me:
            subnet = str(ipaddress.ip_network(f"{me}/24", strict=False))
    if subnet:
        net = ipaddress.ip_network(subnet, strict=False)
        if net.num_addresses > 4096:
            sys.exit("Subnet too large to scan; pass something like --subnet 192.168.1.0/24")
        candidates.update(str(h) for h in net.hosts())

    tvs = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=64) as pool:
        for ip, info in zip(candidates, pool.map(lambda i: get_device_info(i, 1.0), candidates)):
            if info:
                tvs.append((ip, info))
    return sorted(tvs, key=lambda t: ipaddress.ip_address(t[0]))


# --------------------------------------------------------------------------- WebSocket

def ws_connect(ip, token=None, timeout=30):
    try:
        import websocket  # websocket-client
    except ImportError:
        sys.exit("Missing dependency: pip install websocket-client")

    name = base64.b64encode(CLIENT_NAME.encode()).decode()
    url = f"wss://{ip}:{WS_PORT}/api/v2/channels/samsung.remote.control?name={name}"
    if token:
        url += f"&token={token}"
    # The TV uses a self-signed certificate.
    ws = websocket.create_connection(
        url, timeout=timeout, sslopt={"cert_reqs": ssl.CERT_NONE, "check_hostname": False}
    )
    first = json.loads(ws.recv())
    if first.get("event") == "ms.channel.unauthorized":
        ws.close()
        sys.exit("The TV refused the connection. Run `pair` and choose Allow on the TV.")
    if first.get("event") != "ms.channel.connect":
        ws.close()
        sys.exit(f"Unexpected reply from TV: {first}")
    new_token = (first.get("data") or {}).get("token")
    return ws, new_token


def open_session(args):
    ip = resolve_ip(args)
    cfg = load_config()
    ws, token = ws_connect(ip, cfg.get("token"))
    if token and token != cfg.get("token"):
        cfg.update(ip=ip, token=token)
        save_config(cfg)
    return ws


def send_key(ws, key):
    ws.send(json.dumps({
        "method": "ms.remote.control",
        "params": {"Cmd": "Click", "DataOfCmd": key, "Option": "false",
                   "TypeOfRemote": "SendRemoteKey"},
    }))


def installed_apps(ws):
    ws.send(json.dumps({
        "method": "ms.channel.emit",
        "params": {"event": "ed.installedApp.get", "to": "host"},
    }))
    end = time.time() + 15
    while time.time() < end:
        msg = json.loads(ws.recv())
        if msg.get("event") == "ed.installedApp.get":
            return (msg.get("data") or {}).get("data") or []
    return []


# --------------------------------------------------------------------------- commands

def cmd_discover(args):
    print("Scanning the local network for Samsung TVs...")
    tvs = discover(args.subnet)
    if not tvs:
        print("No Samsung TV answered. Check the TV is on (not standby) and on the same "
              "Wi-Fi, and that 'Settings > General > External Device Manager > Device "
              "Connection Manager > Access Notification' is not blocking this computer.")
        return 1
    for ip, info in tvs:
        d = info["device"]
        print(f"  {ip:15}  {d.get('name', '?')}  model={d.get('modelName', '?')}  "
              f"year={d.get('model', '?')[:2]}  os={d.get('OS', '?')}")
    if len(tvs) == 1:
        cfg = load_config()
        cfg["ip"] = tvs[0][0]
        save_config(cfg)
        print(f"Saved {tvs[0][0]} as your TV.")
    return 0


def cmd_info(args):
    ip = resolve_ip(args)
    info = get_device_info(ip, 3.0)
    if not info:
        sys.exit(f"No Samsung TV API answered at {ip}:{REST_PORT}.")
    print(json.dumps(info["device"], indent=2))
    return 0


def cmd_pair(args):
    ip = resolve_ip(args)
    print("Look at the TV now and choose 'Allow' (you have about 30 seconds)...")
    ws, token = ws_connect(ip, None, timeout=35)
    ws.close()
    cfg = load_config()
    cfg["ip"] = ip
    if token:
        cfg["token"] = token
    save_config(cfg)
    print(f"Paired with {ip}." + ("" if token else " (This TV model does not use tokens.)"))
    return 0


def cmd_apps(args):
    ws = open_session(args)
    try:
        apps = installed_apps(ws)
    finally:
        ws.close()
    if not apps:
        print("The TV returned no app list (some models do not support this).")
        return 1
    apps.sort(key=lambda a: a.get("name", "").lower())
    flagged = 0
    for a in apps:
        name = a.get("name", "?")
        bloat = any(h in name.lower() + " " for h in BLOAT_HINTS)
        flagged += bloat
        print(f"  {'[bloat?]' if bloat else '        '}  {name:35}  {a.get('appId')}")
    print(f"\n{len(apps)} apps, {flagged} flagged. To delete one: on the TV go to "
          "Apps > Settings (gear icon) > select the app > Delete. Built-in apps without a "
          "Delete option can only be removed from the home row (highlight > Down > Remove).")
    return 0


def cmd_launch(args):
    ws = open_session(args)
    try:
        ws.send(json.dumps({
            "method": "ms.channel.emit",
            "params": {"event": "ed.apps.launch", "to": "host",
                       "data": {"appId": args.app_id, "action_type": args.action}},
        }))
        time.sleep(1)
    finally:
        ws.close()
    print(f"Asked the TV to open {args.app_id}.")
    return 0


def cmd_key(args):
    ws = open_session(args)
    try:
        for k in args.keys:
            send_key(ws, k if k.startswith("KEY_") else "KEY_" + k.upper())
            time.sleep(args.delay)
    finally:
        ws.close()
    return 0


CHECKLIST = """\
Samsung TV privacy & ad settings (menu names differ a little by model year;
use the TV's search in Settings if a path doesn't match):

  1. Settings > General & Privacy > Terms & Privacy > Privacy Choices
       - Viewing Information Services ......... OFF  (stops ACR: TV watching what's on screen)
       - Interest-Based Advertisements ........ OFF
       - Voice Recognition Services ........... OFF  (if you don't use voice)
       - Reset PSID / ad ID ................... run it once
  2. Settings > General & Privacy > Terms & Privacy
       - Withdraw / un-tick optional agreements (Smart Hub privacy notice add-ons,
         "Interest-Based Ads", "Viewing Information Services").
  3. Settings > General > Smart Features
       - Autorun Smart Hub .................... OFF  (TV opens straight to the input)
       - Autorun Last App ..................... ON or OFF to taste
       - Run Samsung TV Plus automatically .... OFF
  4. Settings > Support > Software Update > Auto Update: your choice (updates can
     re-enable some ad features; recheck this list after a big update).
  5. Home row: highlight each unwanted tile, press Down, choose Remove.
  6. Network: add blocklist/samsung-ads-tracking.txt to Pi-hole, AdGuard Home,
     NextDNS, or your router's domain block list.

To undo: switch the settings back, and remove the blocklist from your DNS filter.
"""


def cmd_checklist(_args):
    print(CHECKLIST)
    return 0


def main(argv=None):
    p = argparse.ArgumentParser(description="Find and control a Samsung TV on your Wi-Fi.")
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("discover", help="find Samsung TVs on the local network")
    s.add_argument("--subnet", help="e.g. 192.168.1.0/24 (default: this computer's /24)")
    s.set_defaults(func=cmd_discover)

    for name, func, h in [("info", cmd_info, "show the TV's details"),
                          ("pair", cmd_pair, "authorise this computer on the TV"),
                          ("apps", cmd_apps, "list installed apps and flag bloat")]:
        s = sub.add_parser(name, help=h)
        s.add_argument("--ip")
        s.set_defaults(func=func)

    s = sub.add_parser("launch", help="open an app by appId")
    s.add_argument("app_id")
    s.add_argument("--action", default="NATIVE_LAUNCH", choices=["NATIVE_LAUNCH", "DEEP_LINK"])
    s.add_argument("--ip")
    s.set_defaults(func=cmd_launch)

    s = sub.add_parser("key", help="send remote keys, e.g. HOME VOLUP KEY_MUTE")
    s.add_argument("keys", nargs="+")
    s.add_argument("--delay", type=float, default=0.4)
    s.add_argument("--ip")
    s.set_defaults(func=cmd_key)

    s = sub.add_parser("checklist", help="print the privacy/ad settings to switch off")
    s.set_defaults(func=cmd_checklist)

    args = p.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
