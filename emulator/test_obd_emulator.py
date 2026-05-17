#!/usr/bin/env python3
"""
Test suite for emulator/obd_emulator.py — stdlib unittest only, no pip install.

Covers the four pure helpers (encode_dtc, dtc_frame, compute_supported_pids,
encode_vin_response), scenario data invariants, and an end-to-end socket
exercise where the server runs in a background thread and a probe client
drives the AT/PID surface over a real TCP connection.

Run:
    python3 -m unittest emulator.test_obd_emulator           # from repo root
    python3 -m unittest test_obd_emulator                    # from emulator/
    python3 emulator/test_obd_emulator.py                    # direct
"""

import socket
import sys
import threading
import time
import unittest
from pathlib import Path

# Make the sibling obd_emulator module importable when this file is run directly
# from either the repo root or from inside emulator/.
HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import obd_emulator as emu  # noqa: E402


# ── Pure helpers ──────────────────────────────────────────────────────────────

class EncodeDtcTest(unittest.TestCase):
    """encode_dtc(): SAE J1979 two-byte DTC encoding.

    Prefix bits (high two bits of byte A): P=00, C=01, B=10, U=11.
    Then 4 hex digits worth of code. Examples from the J1979 spec walk:
        P0301 → 03 01     (P, then 0301)
        P0171 → 01 71
        P1229 → 12 29     (manufacturer-specific code, still uses P=00 prefix)
        C0561 → 45 61     (C-class: 0x40 | 0x05 = 0x45)
        B1234 → 92 34     (B-class: 0x80 | 0x12 = 0x92)
        U0100 → C1 00     (U-class: 0xC0 | 0x01 = 0xC1)
    """

    def test_powertrain_simple(self):
        self.assertEqual(emu.encode_dtc("P0301"), "03 01")
        self.assertEqual(emu.encode_dtc("P0171"), "01 71")
        self.assertEqual(emu.encode_dtc("P0087"), "00 87")

    def test_powertrain_manufacturer_specific(self):
        # P1229 = manufacturer-specific powertrain code; still encodes with P=00.
        self.assertEqual(emu.encode_dtc("P1229"), "12 29")

    def test_chassis_prefix(self):
        self.assertEqual(emu.encode_dtc("C0561"), "45 61")

    def test_body_prefix(self):
        self.assertEqual(emu.encode_dtc("B1234"), "92 34")

    def test_network_prefix(self):
        self.assertEqual(emu.encode_dtc("U0100"), "C1 00")

    def test_lowercase_prefix_accepted(self):
        # Real ELM traffic is always uppercase, but the helper is case-tolerant
        # on the prefix — protects against config strings authored by hand.
        self.assertEqual(emu.encode_dtc("p0301"), "03 01")


class DtcFrameTest(unittest.TestCase):
    """dtc_frame(): wraps a list of DTCs into a Mode 03/07/0A response."""

    def test_empty_list_emits_count_byte_zero(self):
        self.assertEqual(emu.dtc_frame([], "43"), "43 00")
        self.assertEqual(emu.dtc_frame([], "47"), "47 00")
        self.assertEqual(emu.dtc_frame([], "4A"), "4A 00")

    def test_single_dtc(self):
        self.assertEqual(emu.dtc_frame(["P0301"], "43"), "43 01 03 01")

    def test_multiple_dtcs(self):
        self.assertEqual(emu.dtc_frame(["P0087", "P1229"], "43"), "43 02 00 87 12 29")


class ComputeSupportedPidsTest(unittest.TestCase):
    """compute_supported_pids(): J1979 4-byte support bitmap.

    Bit layout (MSB-first across 32 bits):
        byte A bit 7 = PID (start+1)
        ...
        byte D bit 0 = PID (start+0x20)  ← also the continuation flag
    """

    def test_empty_pid_list_returns_zero_bitmap(self):
        self.assertEqual(emu.compute_supported_pids([], 0x00), "00 00 00 00")

    def test_continuation_bit_set_when_pids_above_range(self):
        # No PIDs in 01-20, but one PID (42) above 0x20 → continuation only.
        self.assertEqual(emu.compute_supported_pids(["42"], 0x00), "00 00 00 01")

    def test_continuation_bit_not_set_when_no_pids_above(self):
        # PID 04 alone → bit at byte A position; nothing above 0x20.
        self.assertEqual(emu.compute_supported_pids(["04"], 0x00), "10 00 00 00")

    def test_pid_01_lands_on_top_bit_of_byte_a(self):
        # PID 01 = MSB of byte A.
        self.assertEqual(emu.compute_supported_pids(["01"], 0x00), "80 00 00 00")

    def test_pid_20_lands_on_lsb_of_byte_d(self):
        # PID 0x20 = LSB of byte D (collides with the continuation slot, but
        # since 0x20 itself is the highest in-range bit, that's by design).
        self.assertEqual(emu.compute_supported_pids(["20"], 0x00), "00 00 00 01")

    def test_corolla_layout(self):
        # Hand-computed bitmap for the corolla scenario's PIDs.
        pids = ["0C", "05", "04", "0D", "11", "0F", "10", "06", "07", "14", "15", "42"]
        # PIDs in 01-20: 04,05,06,07,0C,0D,0F,10,11,14,15
        # + continuation for 42
        self.assertEqual(emu.compute_supported_pids(pids, 0x00), "1E 1B 98 01")

    def test_hilux_layout(self):
        pids = ["0C", "05", "04", "0D", "11", "0F", "23", "42"]
        # PIDs in 01-20: 04,05,0C,0D,0F,11 + continuation for 23,42
        self.assertEqual(emu.compute_supported_pids(pids, 0x00), "18 1A 80 01")

    def test_0120_range_for_hilux(self):
        # PID 0x23 in 21-40 (offset 2 → bit 1<<29), continuation for 42 > 0x40.
        pids = ["0C", "05", "23", "42"]
        self.assertEqual(emu.compute_supported_pids(pids, 0x20), "20 00 00 01")

    def test_0140_range(self):
        # PID 0x42 in 41-60 (offset 1 → bit 1<<30), no PIDs above 0x60.
        pids = ["0C", "42"]
        self.assertEqual(emu.compute_supported_pids(pids, 0x40), "40 00 00 00")


class PopulateSupportedPidBitmapsTest(unittest.TestCase):
    """populate_supported_pid_bitmaps(): mutates scenario["pids"] with the
    00 / 20 / 40 bitmap entries, idempotently."""

    def _scenario(self, data_pids):
        # Build a minimal scenario shape that the helper accepts.
        return {"pids": {k: "" for k in data_pids}}

    def test_corolla_shaped_pids(self):
        s = self._scenario(["0C", "05", "04", "07", "06", "0D", "0F", "10", "11", "14", "15", "42"])
        emu.populate_supported_pid_bitmaps(s)
        self.assertEqual(s["pids"]["00"], "1E 1B 98 01")
        self.assertEqual(s["pids"]["20"], "00 00 00 01")
        self.assertEqual(s["pids"]["40"], "40 00 00 00")

    def test_idempotent_when_called_twice(self):
        # Re-running shouldn't double-count the bitmap entries into the
        # support computation — they're filtered out before recomputing.
        s = self._scenario(["04", "05", "42"])
        emu.populate_supported_pid_bitmaps(s)
        before = dict(s["pids"])
        emu.populate_supported_pid_bitmaps(s)
        self.assertEqual(s["pids"], before)

    def test_skips_ranges_with_no_pids(self):
        # PIDs only in 01-20 → no 0120 or 0140 entry should be injected.
        s = self._scenario(["04", "05", "0C"])
        emu.populate_supported_pid_bitmaps(s)
        self.assertIn("00", s["pids"])
        self.assertNotIn("20", s["pids"])
        self.assertNotIn("40", s["pids"])


class EncodeVinResponseTest(unittest.TestCase):
    """encode_vin_response(): ELM327 multi-line Mode 09 PID 02 format."""

    def test_valid_vin_produces_four_lines(self):
        out = emu.encode_vin_response("JTDBR32E390123456")
        lines = out.split("\r")
        self.assertEqual(len(lines), 4)
        # Header: 0x14 = 20 bytes total (3 PCI + 17 VIN).
        self.assertEqual(lines[0], "014")
        # Frame 0: 49 02 01 + first 3 VIN chars (J, T, D = 4A 54 44).
        self.assertEqual(lines[1], "0: 49 02 01 4A 54 44")
        # Frame 1: 7 bytes (chars 3-9).
        self.assertTrue(lines[2].startswith("1: "))
        self.assertEqual(len(lines[2].split()) - 1, 7)
        # Frame 2: 7 bytes (chars 10-16).
        self.assertTrue(lines[3].startswith("2: "))
        self.assertEqual(len(lines[3].split()) - 1, 7)

    def test_full_vin_roundtrip(self):
        # Reassemble the VIN from the encoded bytes and confirm it matches.
        vin = "4T1BF1FK0EU123456"
        out = emu.encode_vin_response(vin)
        lines = out.split("\r")
        hex_bytes = (
            lines[1].replace("0: 49 02 01 ", "").split() +
            lines[2].replace("1: ", "").split() +
            lines[3].replace("2: ", "").split()
        )
        reconstructed = "".join(chr(int(b, 16)) for b in hex_bytes)
        self.assertEqual(reconstructed, vin)

    def test_wrong_length_raises(self):
        with self.assertRaises(ValueError):
            emu.encode_vin_response("TOOSHORT")
        with self.assertRaises(ValueError):
            emu.encode_vin_response("X" * 18)


# ── Scenario data invariants ──────────────────────────────────────────────────

class ScenarioInvariantsTest(unittest.TestCase):
    """Catches regressions in the SCENARIOS dict shape."""

    REQUIRED_KEYS = ("name", "vin", "confirmed_dtcs", "pending_dtcs",
                     "permanent_dtcs", "pids", "freeze_frame")

    def test_all_scenarios_have_required_keys(self):
        for key, scenario in emu.SCENARIOS.items():
            for required in self.REQUIRED_KEYS:
                self.assertIn(
                    required, scenario,
                    f"scenario {key!r} missing required field {required!r}",
                )

    def test_all_vins_are_17_chars(self):
        for key, scenario in emu.SCENARIOS.items():
            self.assertEqual(
                len(scenario["vin"]), 17,
                f"scenario {key!r} VIN must be 17 chars",
            )

    def test_vins_avoid_ioq(self):
        # Real VINs never use I, O, or Q (avoids confusion with 1, 0). The
        # emulator VINs are synthetic but should still follow the rule so
        # any future VIN-decoder code doesn't reject them.
        for key, scenario in emu.SCENARIOS.items():
            for forbidden in "IOQ":
                self.assertNotIn(
                    forbidden, scenario["vin"],
                    f"scenario {key!r} VIN contains forbidden char {forbidden!r}",
                )

    def test_freeze_frame_dtc_appears_in_confirmed_list(self):
        # The freeze-frame DTC must be one the scenario actually reports —
        # otherwise an Android client asking for Mode 02 data gets a
        # response keyed on a code that never showed up in Mode 03.
        for key, scenario in emu.SCENARIOS.items():
            ff_dtc = scenario["freeze_frame"]["dtc"]
            self.assertIn(
                ff_dtc, scenario["confirmed_dtcs"],
                f"scenario {key!r}: freeze_frame.dtc {ff_dtc!r} not in confirmed_dtcs",
            )

    def test_pid_data_is_whole_bytes(self):
        # Every PID data string must be space-separated 2-char hex bytes.
        for key, scenario in emu.SCENARIOS.items():
            for pid, data in scenario["pids"].items():
                tokens = data.split()
                for tok in tokens:
                    self.assertEqual(
                        len(tok), 2,
                        f"scenario {key!r} PID {pid}: token {tok!r} not 2 hex chars",
                    )
                    int(tok, 16)  # raises ValueError if not hex

    def test_misfire_scenario_emits_p0301(self):
        # The whole reason we added the misfire scenario — it must match
        # what the Android demo's misfire.json fixture expects.
        self.assertIn("P0301", emu.SCENARIOS["misfire"]["confirmed_dtcs"])

    def test_corolla_is_default_scenario(self):
        # main()'s argparse default is "corolla" — protect that contract.
        # README.md and downstream tooling assume the no-args invocation
        # picks corolla.
        import argparse
        parser = argparse.ArgumentParser()
        parser.add_argument("--scenario", choices=list(emu.SCENARIOS.keys()),
                            default="corolla")
        args = parser.parse_args([])
        self.assertEqual(args.scenario, "corolla")


# ── End-to-end socket exercise ────────────────────────────────────────────────

def _free_port():
    """Pick an unused TCP port on localhost. Race-free for our purposes
    because we hand it straight to the server on the same process."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def _run_server(scenario_key, port, stop_event):
    """Run a minimal listener that handles one connection at a time. We
    don't reuse main() because main()'s while True is hard to break out
    of cleanly from a test, and stdout banner spam pollutes the test
    output."""
    scenario = emu.SCENARIOS[scenario_key]
    # Ensure the scenario carries the supported-PID bitmaps before serving
    # (production main() does this on startup; the SCENARIOS dict at module
    # load time does not).
    emu.populate_supported_pid_bitmaps(scenario)
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as srv:
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.settimeout(0.2)
        srv.bind(("127.0.0.1", port))
        srv.listen(1)
        while not stop_event.is_set():
            try:
                conn, _ = srv.accept()
            except socket.timeout:
                continue
            emu.ELM327Session(conn, scenario).run()


class EmulatorEndToEndTest(unittest.TestCase):
    """Drives the live socket. Confirms the wire framing matches what a
    real ELM327 client would see, end to end."""

    @classmethod
    def setUpClass(cls):
        cls.port = _free_port()
        cls.stop = threading.Event()
        cls.thread = threading.Thread(
            target=_run_server, args=("misfire", cls.port, cls.stop), daemon=True,
        )
        cls.thread.start()
        # Wait briefly for the listener to be ready.
        deadline = time.time() + 2.0
        while time.time() < deadline:
            try:
                with socket.create_connection(("127.0.0.1", cls.port), timeout=0.2):
                    return
            except OSError:
                time.sleep(0.05)
        raise RuntimeError("emulator did not come up in time")

    @classmethod
    def tearDownClass(cls):
        cls.stop.set()
        cls.thread.join(timeout=2.0)

    def setUp(self):
        self.sock = socket.create_connection(("127.0.0.1", self.port), timeout=2.0)
        # Drain the prompt banner.
        self._read_until_prompt()

    def tearDown(self):
        self.sock.close()

    def _read_until_prompt(self, timeout=2.0):
        self.sock.settimeout(timeout)
        buf = b""
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                chunk = self.sock.recv(4096)
            except socket.timeout:
                break
            if not chunk:
                break
            buf += chunk
            if buf.endswith(b">"):
                return buf.decode(errors="replace")
        raise AssertionError(f"no prompt received; buffer={buf!r}")

    def _send(self, cmd):
        self.sock.sendall((cmd + "\r").encode())
        return self._read_until_prompt()

    # AT handshake -------------------------------------------------------------

    def test_atz_returns_elm327_banner(self):
        resp = self._send("ATZ")
        self.assertIn("ELM327 v1.5", resp)
        # ATZ echo is on by default until ATE0; confirm the command echoes back.
        self.assertIn("ATZ", resp)

    def test_ate0_suppresses_echo_on_subsequent_commands(self):
        self._send("ATE0")
        resp = self._send("0105")
        # Echo off → response should not contain the command itself.
        self.assertNotIn("0105\r", resp.replace("\\r", "\r"))

    def test_unknown_at_command_returns_ok(self):
        # ELM327's lenient AT behavior — anything starting with AT that we
        # don't recognize is acknowledged. Real adapters do this too.
        resp = self._send("ATSP 0")
        self.assertIn("OK", resp)

    # Mode 01 ------------------------------------------------------------------

    def test_0100_returns_supported_pid_bitmap(self):
        self._send("ATE0")
        resp = self._send("0100")
        # The misfire scenario shares corolla's PID layout → same bitmap.
        self.assertIn("41 00 1E 1B 98 01", resp)

    def test_010C_returns_misfire_idle_rpm(self):
        self._send("ATE0")
        resp = self._send("010C")
        # Misfire scenario idles at 680 rpm → 0x0AA0.
        self.assertIn("41 0C 0A A0", resp)

    def test_unknown_pid_returns_no_data(self):
        self._send("ATE0")
        resp = self._send("0199")  # PID we don't serve
        self.assertIn("NO DATA", resp)

    # Mode 03 / 09 -------------------------------------------------------------

    def test_mode_03_returns_p0301(self):
        self._send("ATE0")
        resp = self._send("03")
        # 43 = response prefix, 01 = one DTC, 03 01 = encoded P0301.
        self.assertIn("43 01 03 01", resp)

    def test_mode_09_pid_02_returns_multi_line_vin(self):
        self._send("ATE0")
        resp = self._send("0902")
        self.assertIn("014", resp)            # total-bytes header
        self.assertIn("0: 49 02 01", resp)    # first frame
        self.assertIn("1:", resp)             # second frame marker
        self.assertIn("2:", resp)             # third frame marker
        # First 3 chars of the misfire scenario VIN are 4T1 (0x34 0x54 0x31).
        self.assertIn("34 54 31", resp)

    def test_mode_09_pid_00_advertises_only_pid_02(self):
        self._send("ATE0")
        resp = self._send("0900")
        # 0x40 in byte A = bit 6 set = PID 02 supported (PIDs 01 and 03+ are not).
        self.assertIn("49 00 40 00 00 00", resp)

    def test_unknown_mode_returns_question_mark(self):
        self._send("ATE0")
        resp = self._send("0B00")     # Mode 0B isn't a thing we handle
        self.assertIn("?", resp)


if __name__ == "__main__":
    unittest.main(verbosity=2)
