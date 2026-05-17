# OBD-II Emulator

A small TCP server that speaks the ELM327 AT-command protocol, serving pre-defined vehicle scenarios over a socket. Lets us develop and demo the OBD transport layer without a real ELM327 dongle and a car in front of us.

## Why this exists

Phase 12 of the Android app is the real `BluetoothOBDDataSource` — pairing flow, ELM327 AT handshake, Mode 01/03 round-trips. That work needs a reachable, well-behaved ELM327 to develop against. This emulator is that target. The Android side connects over WiFi instead of Bluetooth, but the bytes on the wire are the same. When BLE GATT comes online, the underlying socket gets swapped; the parsing and state-machine code stays.

It's also a Plan B for car-test sessions when the real hardware is flaky on the day.

See `FUTURE_WORK.md` §"Python TCP OBD emulator over WiFi" for context, and `data/DataSource.kt` for the `EMULATOR` enum value the Android side will tag emulator-sourced snapshots with.

## Run it

Python 3, standard library only. No `pip install`.

```bash
python3 emulator/obd_emulator.py                  # default: corolla scenario, port 35000
python3 emulator/obd_emulator.py --scenario hilux
python3 emulator/obd_emulator.py --port 35000
python3 emulator/obd_emulator.py --host 127.0.0.1 # bind localhost only (default 0.0.0.0 for LAN)
python3 emulator/obd_emulator.py --list           # show available scenarios and their DTCs
```

The server prints connection lifecycle messages to stdout. Ctrl+C to stop.

## Scenarios

| Key | Vehicle | Confirmed DTCs | What it simulates |
|-----|---------|----------------|-------------------|
| `corolla` (default) | 2009 Toyota Corolla 1ZZ-FE petrol | P0171 | Lean condition: low MAF (1.85 g/s), +18% LTFT, lean upstream O2 (0.2V) |
| `hilux` | 2008 Toyota Hilux 2KD-FTV diesel | P0087, P1229 | Fuel rail pressure low: 28,000 kPa vs ~34,500 kPa target, fault confirmed in freeze frame at 23,940 kPa |
| `misfire` | 2014 Toyota Camry 2AR-FE petrol | P0301 | Cylinder 1 misfire: rough idle at 680 rpm, +8% STFT, lean upstream O2 (0.15V); fault set in freeze frame at 2200 rpm / 55% load. Matches `app/src/main/assets/misfire.json`. |

Each scenario carries confirmed/pending/permanent DTC lists, a Mode 01 live-PID table, a Mode 02 freeze-frame snapshot tied to the primary DTC, and a 17-character VIN for Mode 09 PID 02.

## Protocol coverage

- **AT handshake:** `ATZ`, `ATWS`, `ATE0`/`ATE1` (echo), `ATH0`/`ATH1` (headers), `ATSP0`, `ATRV` (battery), `ATI`, `AT@1`. Unknown AT commands return `OK`.
- **Mode 01** — live PIDs (RPM, coolant, load, speed, throttle, IAT, MAF, STFT/LTFT B1, O2 voltages, rail pressure, battery). PIDs `00` / `20` / `40` return the SAE J1979 supported-PIDs bitmap, computed from each scenario's actual PID coverage. Unknown PIDs return `NO DATA`.
- **Mode 02** — freeze-frame data for the scenario's primary DTC.
- **Mode 03 / 07 / 0A** — confirmed / pending / permanent DTCs, J1979 two-byte encoded.
- **Mode 09** — PID `00` (supported info-types) advertises only PID `02`; PID `02` returns the scenario's VIN as an ELM327-style multi-line response (`014` header + `0:` / `1:` / `2:` lines, ATH0 format). Other Mode 09 PIDs return `NO DATA`.

Echo and headers state are tracked per session and respected on every response. Mode 04 (clear DTCs) is intentionally not implemented — the Android app does not expose it.

## Tests

Standard-library `unittest` covers the pure helpers (`encode_dtc`, `dtc_frame`, `compute_supported_pids`, `populate_supported_pid_bitmaps`, `encode_vin_response`), structural invariants of every scenario (VIN length, 2-byte-hex PID data, freeze-frame DTC must appear in `confirmed_dtcs`, etc.), and an end-to-end socket exercise — the test boots the server in a background thread on an ephemeral port and drives the AT / Mode 01 / Mode 03 / Mode 09 surface over a real TCP connection.

```bash
python3 emulator/test_obd_emulator.py            # direct
python3 -m unittest emulator.test_obd_emulator   # from repo root
```

No `pip install` required. Verbose output by default.

## Status

Authored by Will Zhang. Android-side transport layer (`TcpTransport`, `BluetoothOBDDataSource`) is Phase 12 work and not yet wired up — the emulator is intentionally useful-but-unused until that catches up.
