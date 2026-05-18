#!/usr/bin/env python3
"""
Car Copilot OBD-II emulator — TCP server that speaks ELM327 AT commands.

Android connects via WiFi socket (TcpTransport) instead of Bluetooth.
The emulator serves pre-defined vehicle scenarios so the full pipeline
can be developed and demoed without real hardware.

Usage:
    python3 obd_emulator.py                          # Corolla P0171 (default)
    python3 obd_emulator.py --scenario hilux         # Hilux diesel P0087
    python3 obd_emulator.py --cycle 10               # rotate all scenarios every 10 s
    python3 obd_emulator.py --cycle 5 --scenario misfire  # start at misfire, then cycle
    python3 obd_emulator.py --port 35000
    python3 obd_emulator.py --list

    # Interactive control panel in the browser (open http://localhost:35001)
    python3 obd_emulator.py --panel
    python3 obd_emulator.py --panel --panel-port 8080
"""

import curses
import itertools
import json
import socket
import threading
import time
import argparse

HOST = "0.0.0.0"
PORT = 35000


# ── Scenario definitions ──────────────────────────────────────────────────────
#
# pids: Mode 01 PID hex (without leading 0) → response data bytes (hex, space-separated)
# Encoding reference:
#   0C  RPM              ((A*256)+B)/4          800 rpm  → 0C 80
#   05  Coolant °C       A-40                   88°C     → 80
#   04  Engine load %    A*100/255              22%      → 38
#   0D  Speed kph        A                      0        → 00
#   11  Throttle %       A*100/255              14%      → 24
#   0F  IAT °C           A-40                   25°C     → 41
#   10  MAF g/s          ((A*256)+B)/100        1.85     → 00 B9
#   06  STFT B1 %        (A-128)*100/128        +12%     → 8F
#   07  LTFT B1 %        (A-128)*100/128        +18%     → 97
#   14  O2 S1 V          A/200                  0.2V     → 28 FF
#   15  O2 S2 V          A/200                  0.72V    → 90 FF
#   23  Rail pressure kPa ((A*256)+B)*10        28000    → 0A F0
#   42  Battery V        ((A*256)+B)/1000       12.4V    → 30 70

# All scenarios use the same vehicle so the app's hardcoded VehicleInfo stays consistent.
_VIN  = "JTDBR32E390123456"
_NAME = "2009 Toyota Corolla 1ZZ-FE petrol"

SCENARIOS = {
    # ── P0171 — lean condition, dirty MAF ────────────────────────────────────
    "corolla": {
        "name": f"{_NAME} — P0171 lean condition",
        "vin":  _VIN,
        "confirmed_dtcs": ["P0171"],
        "pending_dtcs":   [],
        "permanent_dtcs": ["P0171"],
        "pids": {
            "0C": "0C 80",   # RPM 800
            "05": "80",      # Coolant 88°C
            "04": "38",      # Load 22%
            "0D": "00",      # Speed 0
            "11": "24",      # Throttle 14%
            "0F": "41",      # IAT 25°C
            "10": "00 B9",   # MAF 1.85 g/s (low — lean symptom)
            "06": "8F",      # STFT B1 +12%
            "07": "97",      # LTFT B1 +18% (chronic lean)
            "14": "28 FF",   # O2 S1 0.20V (lean)
            "15": "90 FF",   # O2 S2 0.72V
            "42": "30 70",   # Battery 12.4V
        },
        "freeze_frame": {
            "dtc": "P0171",
            "pids": {
                "0C": "0C 80",   # RPM 800
                "05": "7D",      # Coolant 85°C
                "04": "45",      # Load 27%
                "0D": "00",
                "10": "00 83",   # MAF 1.31 g/s (very low — fault trigger)
                "07": "9F",      # LTFT B1 +23%
            },
        },
    },

    # ── P0301 — cylinder 1 misfire, failed ignition coil ─────────────────────
    "coil": {
        "name": f"{_NAME} — P0301 cylinder 1 misfire (coil failure)",
        "vin":  _VIN,
        "confirmed_dtcs": ["P0301"],
        "pending_dtcs":   ["P0301"],
        "permanent_dtcs": ["P0301"],
        "pids": {
            "0C": "0A A0",   # RPM 680 (rough idle)
            "05": "83",      # Coolant 91°C
            "04": "47",      # Load 28%
            "0D": "00",      # Speed 0
            "11": "29",      # Throttle 16%
            "0F": "43",      # IAT 27°C
            "10": "01 5E",   # MAF 3.50 g/s
            "06": "8A",      # STFT B1 +8%
            "07": "88",      # LTFT B1 +6%
            "14": "1E FF",   # O2 S1 0.15V (lean — unburned air from dead cylinder)
            "15": "8C FF",   # O2 S2 0.70V
            "42": "37 14",   # Battery 14.1V
        },
        "freeze_frame": {
            "dtc": "P0301",
            "pids": {
                "0C": "22 60",   # RPM 2200 (fault set under load)
                "05": "82",      # Coolant 90°C
                "04": "8C",      # Load 55%
                "0D": "32",      # Speed 50 kph
                "11": "51",      # Throttle 32%
                "10": "03 B6",   # MAF 9.50 g/s
            },
        },
    },

    # ── P0171 + P0101 — lean with MAF circuit fault ───────────────────────────
    "lean_maf": {
        "name": f"{_NAME} — P0171+P0101 lean + MAF fault",
        "vin":  _VIN,
        "confirmed_dtcs": ["P0171", "P0101"],
        "pending_dtcs":   ["P0171"],
        "permanent_dtcs": ["P0171"],
        "pids": {
            "0C": "0C 58",   # RPM 790
            "05": "7F",      # Coolant 87°C
            "04": "36",      # Load 21%
            "0D": "00",      # Speed 0
            "11": "22",      # Throttle 13%
            "0F": "41",      # IAT 25°C
            "10": "00 A0",   # MAF 1.60 g/s (lower than corolla — sensor degrading)
            "06": "92",      # STFT B1 +14%
            "07": "9A",      # LTFT B1 +20%
            "14": "24 FF",   # O2 S1 0.18V (lean)
            "15": "88 FF",   # O2 S2 0.68V
            "42": "30 70",   # Battery 12.4V
        },
        "freeze_frame": {
            "dtc": "P0171",
            "pids": {
                "0C": "0C 58",   # RPM 790
                "05": "7D",      # Coolant 85°C
                "04": "42",      # Load 26%
                "0D": "00",
                "10": "00 78",   # MAF 1.20 g/s (very low at fault set)
                "07": "9A",      # LTFT B1 +20%
            },
        },
    },

    # ── P0300 + P0171 — random misfire from fuel starvation ──────────────────
    "misfire_fuel": {
        "name": f"{_NAME} — P0300+P0171 random misfire (fuel starvation)",
        "vin":  _VIN,
        "confirmed_dtcs": ["P0300", "P0171"],
        "pending_dtcs":   ["P0300", "P0171"],
        "permanent_dtcs": ["P0300"],
        "pids": {
            "0C": "0B 18",   # RPM 710 (rough — multiple cylinders affected)
            "05": "81",      # Coolant 89°C
            "04": "3D",      # Load 24%
            "0D": "00",      # Speed 0
            "11": "20",      # Throttle 13%
            "0F": "41",      # IAT 25°C
            "10": "00 8C",   # MAF 1.40 g/s (very low — starved)
            "06": "94",      # STFT B1 +16%
            "07": "9C",      # LTFT B1 +22%
            "14": "24 FF",   # O2 S1 0.18V (very lean)
            "15": "82 FF",   # O2 S2 0.65V
            "42": "30 D4",   # Battery 12.5V
        },
        "freeze_frame": {
            "dtc": "P0300",
            "pids": {
                "0C": "0B 18",   # RPM 710
                "05": "81",      # Coolant 89°C
                "04": "4C",      # Load 30%
                "0D": "00",
                "10": "00 8C",   # MAF 1.40 g/s
                "07": "9C",      # LTFT B1 +22%
            },
        },
    },

    # ── P0420 — catalyst efficiency below threshold ───────────────────────────
    "catalyst": {
        "name": f"{_NAME} — P0420 catalytic converter degraded",
        "vin":  _VIN,
        "confirmed_dtcs": ["P0420"],
        "pending_dtcs":   [],
        "permanent_dtcs": ["P0420"],
        "pids": {
            "0C": "0C 80",   # RPM 800 (normal idle — no obvious symptoms)
            "05": "7E",      # Coolant 86°C
            "04": "33",      # Load 20%
            "0D": "00",      # Speed 0
            "11": "22",      # Throttle 13%
            "0F": "41",      # IAT 25°C
            "10": "01 54",   # MAF 3.40 g/s (normal)
            "06": "83",      # STFT B1 +2% (normal)
            "07": "84",      # LTFT B1 +3% (normal)
            "14": "5A FF",   # O2 S1 0.45V (switching normally upstream)
            "15": "50 FF",   # O2 S2 0.40V (should be ~0.1V if cat working — high = degraded)
            "42": "30 D4",   # Battery 12.5V
        },
        "freeze_frame": {
            "dtc": "P0420",
            "pids": {
                "0C": "0C 80",   # RPM 800
                "05": "7E",      # Coolant 86°C
                "04": "33",      # Load 20%
                "0D": "00",
                "14": "5A FF",   # O2 S1 0.45V
                "15": "50 FF",   # O2 S2 0.40V (not converting)
            },
        },
    },

    # ── P0507 — idle too high, vacuum leak ────────────────────────────────────
    "idle_high": {
        "name": f"{_NAME} — P0507 idle too high (vacuum leak)",
        "vin":  _VIN,
        "confirmed_dtcs": ["P0507"],
        "pending_dtcs":   ["P0507"],
        "permanent_dtcs": [],
        "pids": {
            "0C": "11 F8",   # RPM 1150 (should be ~750-800)
            "05": "7D",      # Coolant 85°C
            "04": "24",      # Load 14% (low — unloaded engine at high idle)
            "0D": "00",      # Speed 0
            "11": "1F",      # Throttle 12%
            "0F": "41",      # IAT 25°C
            "10": "01 40",   # MAF 3.20 g/s (high for idle — extra unmetered air)
            "06": "78",      # STFT B1 -6% (ECU cutting fuel for excess air)
            "07": "7B",      # LTFT B1 -4%
            "14": "46 FF",   # O2 S1 0.35V (slightly lean)
            "15": "6E FF",   # O2 S2 0.55V
            "42": "30 D4",   # Battery 12.5V
        },
        "freeze_frame": {
            "dtc": "P0507",
            "pids": {
                "0C": "11 F8",   # RPM 1150
                "05": "7D",      # Coolant 85°C
                "04": "24",      # Load 14%
                "0D": "00",
                "06": "78",      # STFT -6%
                "07": "7B",      # LTFT -4%
            },
        },
    },

    # ── P0401 — EGR flow insufficient ────────────────────────────────────────
    "egr": {
        "name": f"{_NAME} — P0401 EGR flow insufficient",
        "vin":  _VIN,
        "confirmed_dtcs": ["P0401"],
        "pending_dtcs":   [],
        "permanent_dtcs": ["P0401"],
        "pids": {
            "0C": "0C 08",   # RPM 770
            "05": "84",      # Coolant 92°C (slightly high — no EGR cooling combustion)
            "04": "30",      # Load 19%
            "0D": "00",      # Speed 0
            "11": "20",      # Throttle 13%
            "0F": "41",      # IAT 25°C
            "10": "01 18",   # MAF 2.80 g/s
            "06": "89",      # STFT B1 +7%
            "07": "8C",      # LTFT B1 +9%
            "14": "4C FF",   # O2 S1 0.38V
            "15": "78 FF",   # O2 S2 0.60V
            "42": "30 D4",   # Battery 12.5V
        },
        "freeze_frame": {
            "dtc": "P0401",
            "pids": {
                "0C": "0C 08",   # RPM 770
                "05": "84",      # Coolant 92°C
                "04": "30",      # Load 19%
                "0D": "00",
                "10": "01 18",   # MAF 2.80 g/s
                "07": "8C",      # LTFT +9%
            },
        },
    },
}


# ── Supported-PID bitmap (Mode 01 PIDs 00 / 20 / 40) ──────────────────────────
#
# SAE J1979: querying PID 00/20/40 returns a 4-byte bitmap covering the next
# 32 PIDs. Bit 7 of byte A = PID (start+1), …, bit 0 of byte D = PID (start+0x20).
# The LSB of byte D is the continuation flag — set if any PIDs above the
# range are supported (i.e., the next bitmap query is valid).

def compute_supported_pids(pid_keys: list, start_hex: int) -> str:
    """Return a J1979 4-byte support bitmap (hex, space-separated) for the
    range (start_hex, start_hex + 0x20]. Sets the continuation LSB if any
    PIDs above the range exist."""
    bits = 0
    for pid_str in pid_keys:
        pid = int(pid_str, 16)
        if start_hex < pid <= start_hex + 0x20:
            offset = pid - start_hex - 1   # 0..31
            bits |= 1 << (31 - offset)
    if any(int(p, 16) > start_hex + 0x20 for p in pid_keys):
        bits |= 1
    return (f"{(bits >> 24) & 0xFF:02X} {(bits >> 16) & 0xFF:02X} "
            f"{(bits >> 8) & 0xFF:02X} {bits & 0xFF:02X}")


def populate_supported_pid_bitmaps(scenario: dict) -> None:
    """Inject Mode 01 PID 00 / 20 / 40 entries into the scenario's pids dict,
    computed from the scenario's actual PID coverage. Idempotent."""
    data_keys = [k for k in scenario["pids"].keys() if k not in ("00", "20", "40")]
    for start_hex, key in ((0x00, "00"), (0x20, "20"), (0x40, "40")):
        in_range = any(start_hex < int(p, 16) <= start_hex + 0x20 for p in data_keys)
        above    = any(int(p, 16) > start_hex + 0x20 for p in data_keys)
        if in_range or above:
            scenario["pids"][key] = compute_supported_pids(data_keys, start_hex)


# ── Mode 09 VIN encoding (PID 0902) ───────────────────────────────────────────
#
# ELM327 v1.5 with ATH0 emits the VIN as a multi-line response:
#
#   014                          ← 0x14 = 20 bytes of payload (3 header + 17 VIN)
#   0: 49 02 01 V1 V2 V3
#   1: V4 V5 V6 V7 V8 V9 V10
#   2: V11 V12 V13 V14 V15 V16 V17

def encode_vin_response(vin: str) -> str:
    if len(vin) != 17:
        raise ValueError(f"VIN must be 17 chars, got {len(vin)}: {vin!r}")
    b = [f"{ord(c):02X}" for c in vin]
    return "\r".join([
        "014",
        "0: 49 02 01 " + " ".join(b[0:3]),
        "1: "         + " ".join(b[3:10]),
        "2: "         + " ".join(b[10:17]),
    ])


# ── DTC encoding ──────────────────────────────────────────────────────────────

def encode_dtc(dtc: str) -> str:
    """Convert 'P0171' → '01 71' (SAE J1979 two-byte encoding)."""
    prefix_bits = {"P": 0, "C": 4, "B": 8, "U": 12}[dtc[0].upper()]
    digits = dtc[1:]
    high = (prefix_bits + int(digits[0])) << 4 | int(digits[1], 16)
    low = int(digits[2:], 16)
    return f"{high:02X} {low:02X}"


def dtc_frame(dtcs: list, response_byte: str) -> str:
    """Build a Mode 03/07/0A response frame."""
    if not dtcs:
        return f"{response_byte} 00"
    return f"{response_byte} {len(dtcs):02X} " + " ".join(encode_dtc(d) for d in dtcs)


# ── ELM327 session ────────────────────────────────────────────────────────────

class ELM327Session:
    def __init__(self, conn: socket.socket, scenario: dict, log=print):
        self.conn = conn
        self.scenario = scenario
        self.echo = True
        self.headers = False
        self.buf = ""
        self._log = log

    def send(self, text: str):
        self.conn.sendall((text + "\r\r>").encode())

    def prompt(self):
        self.conn.sendall(b">")

    def handle(self, raw: str):
        cmd = raw.strip().upper()
        if not cmd:
            self.prompt()
            return

        if cmd.startswith("AT"):
            self._handle_at(cmd[2:])
            return

        mode = cmd[:2]
        pid  = cmd[2:4] if len(cmd) >= 4 else ""

        if mode == "03":
            self.send(self._wrap(dtc_frame(self.scenario["confirmed_dtcs"], "43")))
        elif mode == "07":
            self.send(self._wrap(dtc_frame(self.scenario["pending_dtcs"], "47")))
        elif mode == "0A":
            self.send(self._wrap(dtc_frame(self.scenario["permanent_dtcs"], "4A")))
        elif mode == "01":
            data = self.scenario["pids"].get(pid)
            if data:
                self.send(self._wrap(f"41 {pid} {data}"))
            else:
                self.send("NO DATA")
        elif mode == "02":
            ff = self.scenario.get("freeze_frame", {})
            data = ff.get("pids", {}).get(pid)
            if data:
                self.send(self._wrap(f"42 {pid} 00 {data}"))
            else:
                self.send("NO DATA")
        elif mode == "09":
            if pid == "00":
                # Only 0902 (VIN) implemented → bit 6 of byte A set (PID 02)
                self.send(self._wrap("49 00 40 00 00 00"))
            elif pid == "02":
                vin = self.scenario.get("vin")
                if vin:
                    self.send(encode_vin_response(vin))
                else:
                    self.send("NO DATA")
            else:
                self.send("NO DATA")
        else:
            self.send("?")

    def _handle_at(self, at: str):
        if at in ("Z", "WS"):
            self.echo = True
            self.headers = False
            self.send("ELM327 v1.5")
        elif at == "E0":
            self.echo = False
            self.send("OK")
        elif at == "E1":
            self.echo = True
            self.send("OK")
        elif at == "H1":
            self.headers = True
            self.send("OK")
        elif at == "H0":
            self.headers = False
            self.send("OK")
        elif at == "RV":
            self.send("12.4V")
        elif at == "I":
            self.send("ELM327 v1.5")
        elif at == "@1":
            self.send("OBDII to RS232 Interpreter")
        else:
            self.send("OK")

    def _wrap(self, resp: str) -> str:
        if not self.headers:
            return resp
        byte_count = len(resp.replace(" ", "")) // 2
        return f"7E8 {byte_count:02X} {resp}"

    def run(self):
        self._log("  [+] client connected")
        self.prompt()
        try:
            while True:
                chunk = self.conn.recv(256).decode(errors="ignore")
                if not chunk:
                    break
                self.buf += chunk
                while True:
                    for sep in ("\r\n", "\r", "\n"):
                        if sep in self.buf:
                            cmd, self.buf = self.buf.split(sep, 1)
                            if self.echo and cmd:
                                self.conn.sendall((cmd + "\r").encode())
                            self.handle(cmd)
                            break
                    else:
                        break
        except (ConnectionResetError, BrokenPipeError, OSError):
            pass
        finally:
            self._log("  [-] client disconnected")
            self.conn.close()


# ── TUI ───────────────────────────────────────────────────────────────────────

def _tui(stdscr, current_scenario, scenarios, obd_port):
    curses.curs_set(0)
    curses.use_default_colors()
    curses.init_pair(1, curses.COLOR_GREEN,  -1)  # active scenario
    curses.init_pair(2, curses.COLOR_BLACK, curses.COLOR_WHITE)  # cursor row

    keys = list(scenarios.keys())
    cursor = 0

    while True:
        stdscr.clear()
        h, w = stdscr.getmaxyx()

        # Left panel is 24 chars wide; right panel gets the rest.
        lw = 24
        rx = lw + 1   # right panel x start

        active_key = next((k for k, v in scenarios.items() if v is current_scenario[0]), None)
        selected_key = keys[cursor]

        # ── left panel ──────────────────────────────────────────────────────
        _add(stdscr, 0, 0, "CAR COPILOT", curses.A_BOLD)
        _add(stdscr, 1, 0, f"port {obd_port}", curses.A_DIM)
        _add(stdscr, 2, 0, "─" * lw)

        for i, key in enumerate(keys):
            s = scenarios[key]
            is_cursor = i == cursor
            is_active = key == active_key
            row = 3 + i * 3

            mark  = "●" if is_active else "○"
            label = f" {mark} {key}"
            dtcs  = " ".join(s["confirmed_dtcs"]) or "—"

            if is_cursor:
                _add(stdscr, row,     0, label.ljust(lw), curses.color_pair(2))
                _add(stdscr, row + 1, 2, dtcs[:lw - 2],  curses.color_pair(2))
            elif is_active:
                _add(stdscr, row,     0, label, curses.color_pair(1) | curses.A_BOLD)
                _add(stdscr, row + 1, 2, dtcs[:lw - 2], curses.color_pair(1))
            else:
                _add(stdscr, row,     0, label)
                _add(stdscr, row + 1, 2, dtcs[:lw - 2], curses.A_DIM)

        with _conn_lock:
            n = _conn_count[0]
        conn_line = f" ● app connected" if n > 0 else " ○ waiting for app"
        conn_attr = curses.color_pair(1) if n > 0 else curses.A_DIM

        bot = h - 6
        _add(stdscr, bot,     0, "─" * lw)
        _add(stdscr, bot + 1, 0, conn_line, conn_attr)
        _add(stdscr, bot + 2, 0, "─" * lw)
        _add(stdscr, bot + 3, 0, " ↑↓  navigate",  curses.A_DIM)
        _add(stdscr, bot + 4, 0, " ↵   activate",  curses.A_DIM)
        _add(stdscr, bot + 5, 0, " q   quit",      curses.A_DIM)

        # Vertical divider
        for row in range(h):
            _add(stdscr, row, lw, "│")

        # ── right panel: full scenario JSON ─────────────────────────────────
        title = f" {selected_key}"
        if selected_key == active_key:
            title += "  [ACTIVE]"
        _add(stdscr, 0, rx, title, curses.A_BOLD)
        _add(stdscr, 1, rx, "─" * max(0, w - rx - 1))

        json_lines = json.dumps(scenarios[selected_key], indent=2).splitlines()
        for i, line in enumerate(json_lines):
            if 2 + i >= h:
                break
            _add(stdscr, 2 + i, rx, line[:max(0, w - rx - 1)])

        stdscr.refresh()

        ch = stdscr.getch()
        if ch == curses.KEY_UP:
            cursor = (cursor - 1) % len(keys)
        elif ch == curses.KEY_DOWN:
            cursor = (cursor + 1) % len(keys)
        elif ch in (curses.KEY_ENTER, 10, 13):
            current_scenario[0] = scenarios[selected_key]
        elif ch in (ord("q"), ord("Q"), 27):
            break


def _add(stdscr, row, col, text, attr=0):
    """addstr that silently ignores out-of-bounds writes."""
    h, w = stdscr.getmaxyx()
    if row < 0 or row >= h or col < 0 or col >= w:
        return
    text = str(text)[:max(0, w - col)]
    try:
        stdscr.addstr(row, col, text, attr)
    except curses.error:
        pass


# ── OBD TCP server ────────────────────────────────────────────────────────────

_conn_lock = threading.Lock()
_conn_count = [0]


def _serve(host, port, current_scenario):
    def _inc():
        with _conn_lock: _conn_count[0] += 1
    def _dec():
        with _conn_lock: _conn_count[0] -= 1

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as srv:
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind((host, port))
        srv.listen(5)
        while True:
            conn, _ = srv.accept()
            scenario = current_scenario[0]
            session = ELM327Session(conn, scenario, log=lambda *_: None)
            _inc()
            def _run(s=session):
                try: s.run()
                finally: _dec()
            threading.Thread(target=_run, daemon=True).start()


# ── Entry point ───────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Car Copilot OBD-II emulator")
    parser.add_argument("--scenario", choices=list(SCENARIOS.keys()), default="corolla",
                        help="Starting scenario (default: corolla)")
    parser.add_argument("--cycle", type=int, default=0, metavar="SECS",
                        help="Rotate through all scenarios every N seconds (0 = disabled)")
    parser.add_argument("--port", type=int, default=PORT)
    parser.add_argument("--host", default=HOST)
    parser.add_argument("--list", action="store_true", help="List available scenarios and exit")
    args = parser.parse_args()

    if args.list:
        for key, s in SCENARIOS.items():
            print(f"  {key:12s}  {s['name']}")
            print(f"              DTCs: {s['confirmed_dtcs']}")
        return

    for s in SCENARIOS.values():
        populate_supported_pid_bitmaps(s)

    scenario_keys = list(SCENARIOS.keys())
    start_idx = scenario_keys.index(args.scenario)
    rotator = itertools.cycle(scenario_keys[start_idx:] + scenario_keys[:start_idx])
    current_scenario = [SCENARIOS[next(rotator)]]

    # OBD TCP server runs in a daemon thread; TUI owns the main thread.
    threading.Thread(
        target=_serve, args=(args.host, args.port, current_scenario), daemon=True
    ).start()

    if args.cycle:
        def _rotate():
            while True:
                time.sleep(args.cycle)
                current_scenario[0] = SCENARIOS[next(rotator)]
        threading.Thread(target=_rotate, daemon=True).start()

    try:
        curses.wrapper(_tui, current_scenario, SCENARIOS, args.port)
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
