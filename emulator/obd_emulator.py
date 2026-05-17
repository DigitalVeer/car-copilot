#!/usr/bin/env python3
"""
Car Copilot OBD-II emulator — TCP server that speaks ELM327 AT commands.

Android connects via WiFi socket (TcpTransport) instead of Bluetooth.
The emulator serves pre-defined vehicle scenarios so the full pipeline
can be developed and demoed without real hardware.

Usage:
    python3 obd_emulator.py                     # Corolla P0171 (default)
    python3 obd_emulator.py --scenario hilux    # Hilux diesel P0087
    python3 obd_emulator.py --port 35000
    python3 obd_emulator.py --list
"""

import socket
import threading
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

SCENARIOS = {
    "corolla": {
        "name": "2009 Toyota Corolla 1ZZ-FE petrol — P0171 lean condition",
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
            "06": "8F",      # STFT B1 +12% (ECU trimming rich)
            "07": "97",      # LTFT B1 +18% (chronic lean)
            "14": "28 FF",   # O2 S1 0.2V (lean)
            "15": "90 FF",   # O2 S2 0.72V
            "42": "30 70",   # Battery 12.4V
        },
        "freeze_frame": {
            "dtc": "P0171",
            "pids": {
                "0C": "0C 80",   # RPM 800 at fault set
                "05": "7D",      # Coolant 85°C
                "04": "45",      # Load 27%
                "0D": "00",
                "10": "00 83",   # MAF 1.31 g/s (very low — fault trigger)
                "07": "9F",      # LTFT B1 +22.7%
            },
        },
    },

    "hilux": {
        "name": "2008 Toyota Hilux 2KD-FTV diesel — P0087 fuel rail pressure low",
        "confirmed_dtcs": ["P0087", "P1229"],
        "pending_dtcs":   ["P0087"],
        "permanent_dtcs": ["P0087"],
        "pids": {
            "0C": "0B B8",   # RPM 750
            "05": "7D",      # Coolant 85°C
            "04": "40",      # Load 25%
            "0D": "00",      # Speed 0
            "11": "1A",      # Throttle 10%
            "0F": "3F",      # IAT 23°C
            "23": "0A F0",   # Fuel rail 28,000 kPa (low; target ~34,500)
            "42": "30 D4",   # Battery 12.5V
        },
        "freeze_frame": {
            "dtc": "P0087",
            "pids": {
                "0C": "0B B8",   # RPM 750
                "05": "75",      # Coolant 77°C (cold start — fault set early)
                "04": "4C",      # Load 30%
                "0D": "00",
                "23": "09 5A",   # Rail pressure 23,940 kPa (very low at fault set)
            },
        },
    },
}


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
    def __init__(self, conn: socket.socket, scenario: dict):
        self.conn = conn
        self.scenario = scenario
        self.echo = True
        self.headers = False
        self.buf = ""

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
        print(f"  [+] client connected")
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
            print(f"  [-] client disconnected")
            self.conn.close()


# ── Entry point ───────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Car Copilot OBD-II emulator")
    parser.add_argument("--scenario", choices=list(SCENARIOS.keys()), default="corolla")
    parser.add_argument("--port", type=int, default=PORT)
    parser.add_argument("--host", default=HOST)
    parser.add_argument("--list", action="store_true", help="List available scenarios and exit")
    args = parser.parse_args()

    if args.list:
        for key, s in SCENARIOS.items():
            print(f"  {key:12s}  {s['name']}")
            print(f"              DTCs: {s['confirmed_dtcs']}")
        return

    scenario = SCENARIOS[args.scenario]
    print(f"Car Copilot OBD Emulator")
    print(f"  scenario : {scenario['name']}")
    print(f"  DTCs     : {scenario['confirmed_dtcs']}")
    print(f"  address  : {args.host}:{args.port}")
    print()

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as srv:
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind((args.host, args.port))
        srv.listen(5)
        print("Waiting for connection  (Ctrl+C to stop)\n")
        try:
            while True:
                conn, addr = srv.accept()
                print(f"Connection from {addr[0]}:{addr[1]}")
                t = threading.Thread(target=ELM327Session(conn, scenario).run, daemon=True)
                t.start()
        except KeyboardInterrupt:
            print("\nStopped.")


if __name__ == "__main__":
    main()
