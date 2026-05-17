# Angaza — Technical Context & Architecture Reference

> Hackathon: Gemma 4 Good — Kaggle | Deadline: May 18, 2026

---

## Product Identity

**Name:** Angaza ("illuminate" in Swahili)

**Tagline:** "Your car's doctor. No mechanic needed."

**Mission:** Give every vehicle owner in the developing world the same diagnostic intelligence as a dealer mechanic — offline, in their language, on hardware they already own.

**Differentiators vs FIXD:**

| Dimension | FIXD | Angaza |
|---|---|---|
| Hardware | $60 proprietary dongle | Any $8 ELM327 |
| Connectivity | Requires internet | Offline-first edge AI |
| Market | US car owners | Global, developing markets |
| Output | Plain English codes | Illustrated DIY repair guides |
| AI | Cloud, gated by paywall | On-device Gemma 4, always free |
| Model | Subscription | Open source |
| Proactive monitoring | Premium only | Core feature |

---

## Target Market

**Primary user:** Rural vehicle owner/operator in Sub-Saharan Africa or SE Asia. $15–40/month income. Old Android phone. Spotty or no internet. No nearby mechanic.

**Connectivity reality:**
- 22.7% of rural Africans use the internet at all
- Rural-urban gap widened to 54% in 2023
- 25% of rural Africa has no mobile broadband coverage
- 1GB data costs up to 1/3 of monthly income in some countries
- When connected: 2G/3G speeds, rationed data bundles
- WiFi exists at petrol stations, churches, homes — not in the field

**Design principle:** The app must work identically in airplane mode as it does with full bars. Connectivity is a bonus, never a dependency.

---

## Target Vehicles

Covers ~75% of the vehicle fleet in Sub-Saharan Africa and SE Asia.

### Priority 1
- Toyota Hilux 2KD-FTV diesel (2001–2015) — dominant rural/commercial East Africa
- Toyota Corolla 1NZ-FE / 1ZZ-FE (2000–2008) — most common passenger car
- Toyota Hiace 2KD-FTV (2003–2013) — matatu/minibus, high economic urgency

### Priority 2
- Toyota Land Cruiser 70 series diesel
- Toyota Vitz / Yaris 1SZ-FE
- Nissan Hardbody / NP300
- Isuzu D-Max 4JK1 diesel

### Priority 3
- Honda Civic / Fit (R18A / L15A) — SE Asia
- Mitsubishi L200 — SE Asia, Latin America
- VW Polo — Southern Africa

**Why Toyota dominates:** Toyota holds 43% of new car sales in Africa. 80%+ of imports are used Japanese vehicles. Parts availability drives purchase decisions, which concentrates the fleet further. The 2KD-FTV diesel engine is the single most important engine to support.

---

## Hardware BOM

### Total estimated cost: ~$20–30 additional beyond phone

**ELM327 Bluetooth dongle (~$10–15)**
- Classic Bluetooth SPP (not WiFi, not BLE)
- BAFX Products or Veepeak — reliable brands
- Reads DTC modes 03 (confirmed), 07 (pending), 0A (permanent)
- Powers from OBD port pin 16 (12V permanent)
- Returns VIN via Mode 9 PID 02 (0x0902)

**ESP32-C3 mini supermini (~$3–4)**
- Receives BLE commands from Android app
- Drives RGB LED + piezo buzzer
- Powered from OBD 12V via mini buck converter inside enclosure
- Firmware: ~50 lines Arduino — listen for BLE command, set LED

**RGB LED (~$0.50)**
- 5mm diffused
- Hot glue drop over hole = soft circular glow (not harsh point)

**Piezo buzzer (~$1)**
- Passive type
- Coded alert tones

**Mini 12V→5V buck converter (~$2–3)**
- Powers everything from OBD pin 16
- No USB ports used. No battery needed. Always on.

**Enclosure**
- Now: Hammond 1551MINI black ABS box (~$6–8). Drill 3mm LED hole.
- Stretch: 3D printed matte black PETG clamshell (~4hr print)
- Face: product name label/emboss + single LED window
- OBD connector hardwired (not plugged) — looks intentional

### LED Behavior

```
Slow green pulse    → Connected, all clear
Fast blue pulse     → Gemma inference running (money shot for judges)
Amber pulse         → Pending / warning fault
Urgent red blink    → Active critical fault
Single beep         → Connected successfully
Double beep         → Pending code detected
Triple slow beep    → Active fault
Rapid beeps         → Critical — stop driving
```

---

## Communication Architecture

```
Car OBD port ──[12V power]──► ELM327 ──[Classic BT SPP]──► Android app
                                                                   │
                                                      [BLE command]▼
                                                    ESP32-C3 mini
                                                      ├── RGB LED
                                                      └── Piezo buzzer

Android app ──[WiFi, opportunistic only]──► FastAPI backend
                                               ├── Parts pricing by region
                                               ├── Anti-scam mechanic guidance
                                               └── Mechanic locator
```

**Two simultaneous Bluetooth connections on Android:**
1. ELM327 — Classic BT for vehicle data (in)
2. ESP32 — BLE for LED/buzzer commands (out)

Android handles both simultaneously without issue.

---

## AI / Inference Stack

### Always offline — Gemma 4 E2B (text)
- ~1.3GB INT4 quantized
- 3–8 second inference on mid-range Android
- LiteRT-LM via Android (memory-mapped, not loaded into RAM all at once)
- 35+ languages out of the box — set via system prompt locale
- Handles: DTC interpretation, plain language output, structured diagnosis

### Offline stretch — Gemma 4 E4B (vision)
- ~2.5GB INT4 quantized
- 15–30 second inference (acceptable for deliberate query)
- User points camera at engine → model identifies component location
- Requires 8GB RAM device (Pixel 8 or equivalent for demo)
- Downscale image to 512px before inference to reduce latency

### Online — Cloud model (Gemini / cloud Gemma)
- Parts sourcing and pricing
- Complex multi-code reasoning
- High-res image analysis
- Mechanic locator queries

### Gemma function calling (online)
```python
tools = [{
    "name": "query_market_data",
    "description": "Get fair parts prices and mechanic guidance for a specific repair in the user's region.",
    "parameters": {
        "repair_type": "string",
        "vehicle": "string",
        "region": "string"
    }
}]
```
Gemma decides when to call it. Fires only when connected to WiFi.

---

## Inference Pipeline

```
VehicleState
  (VIN + DTCs + live PIDs + freeze frame + user symptom buttons)
        ↓
Rules engine (Kotlin, deterministic, sourced from TSBs + iATN)
  → Probabilistic cause ranking adjusted by:
     - PID values (MAF low → MAF sensor; LTFT high + MAF normal → vacuum leak)
     - Companion codes (P0171 + P0174 → upstream, not injectors)
     - Vehicle mileage (220k km → fuel pump more likely than sensor)
     - Phone GPS region (dusty Kenya → MAF contamination ×2)
     - Season from date (dry season → dust ingestion spike)
     - User symptom report (rough idle → vacuum leak ×3)
        ↓
RAG retrieval
  → Top 2 probable causes + vehicle-specific repair context
        ↓
Gemma structured prompt (~300 token input → ~150 token output)
  → Plain language response in user's locale language
  → Confidence tier: HIGH / MEDIUM / LOW
  → Safe to drive: yes / no / conditionally
  → Urgency: now / this week / eventually
        ↓
LED command → ESP32 via BLE
TTS output → Piper (Swahili/Indonesian) or Android TTS
```

**Key principle:** Gemma does not derive the diagnosis. The rules engine does. Gemma's job is natural language assembly — taking structured output and rendering it into a calm, clear, actionable sentence in the user's language. This is a task a small quantized model can do reliably.

---

## Diagnostic Confidence Tiers

**HIGH** — Code matches Toyota/Honda TSB for this engine. Multiple confirmed owner cases in CarComplaints or iATN. Decision tree validated against real cases.

**MEDIUM** — General SAE diagnostic pattern applies. No vehicle-specific TSB found. Generic cause ranking from community data.

**LOW** — Limited data for this vehicle/code combination. Gemma output: *"I have limited data on this. Connect to WiFi for a more complete analysis."*

The confidence tier is displayed honestly in the UI — not a fake percentage but a direct reflection of source quality.

---

## RAG Knowledge Base

### File structure
```
assets/rag/
  generic_dtcs.json        # ~2,000 P0/P2 SAE codes — download from GitHub
  toyota_p1_codes.json     # ~300 Toyota-specific — scrape troublecodes.net
  vehicle_profiles.json    # Engine-specific failure patterns (curated)
  regional_context.json    # Parts prices, market names, scam flags by region
```

### Entry format
```json
{
  "code": "P0171",
  "engine": "2KD-FTV",
  "confidence_tier": "HIGH",
  "source": "Toyota TSB EG-0047T + 847 CarComplaints reports",
  "decision_tree": [
    {
      "step": 1,
      "check": "MAF sensor reading",
      "if_low": "Clean MAF with electronics cleaner — resolves 60% of P0171 on 2KD",
      "if_normal": "proceed to step 2"
    },
    {
      "step": 2,
      "check": "Intake hose visual inspection after air filter box",
      "if_cracked": "Replace hose — $5–15 part, 20 min DIY",
      "if_intact": "proceed to step 3"
    },
    {
      "step": 3,
      "check": "Fuel pressure",
      "note": "Requires gauge — mechanic visit recommended"
    }
  ],
  "diy_possible": true,
  "cost_usd": [0, 15],
  "urgency": "this_week",
  "safe_to_drive": true,
  "companion_codes": ["P0174", "P0101", "P0102"],
  "regional_note": "MAF contamination primary cause in dusty East Africa. Cleaning resolves 60% of cases."
}
```

### Data sources
- **Generic P0/P2:** github.com/myTunecode/obd-codes, python-OBD repo
- **Toyota P1xxx:** troublecodes.net/toyota (scrape), engine-light-help.com
- **TSBs:** NHTSA TSB database (free API), Toyota service PDFs
- **Failure frequency:** CarComplaints.com (28k+ owner complaints by model)
- **Professional cases:** iATN forum archives (subscription, best source)
- **Regional context:** Manual curation from East African automotive forums

---

## Backend API (Online Features Only)

**Stack:** Python FastAPI on Render free tier. Hardcoded JSON files for hackathon. No database needed. Deploy in ~30 minutes.

### Endpoint
```
POST /query
{
  "type": "parts" | "mechanic",
  "dtc": "P0171",
  "vehicle": "Toyota Hilux 2008",
  "region": "east_africa"
}
```

### Response format
```json
{
  "part": "MAF sensor",
  "oem_number": "22204-0L010",
  "aftermarket": ["Denso 197-6192", "Standard Motor MF21990"],
  "price_range_usd": [15, 45],
  "local_market": "Kirinyaga Road, Nairobi",
  "local_name": "Mass air flow meter",
  "fake_warning": "Check Toyota hologram sticker — counterfeits common",
  "mechanic_fair_labor_usd": [10, 25],
  "repair_duration_hours": [1, 2],
  "scam_flags": [
    "Mechanic says needs ECU replacement for a lean code",
    "Quoted over $50 for this repair",
    "Says needs special parts from city"
  ],
  "questions_to_ask": [
    "Can you show me the old part after you remove it?",
    "What parts are you replacing exactly?",
    "Can I watch while you work?"
  ]
}
```

### Design constraints for 3G users
- Response payload < 5KB
- No images in API responses
- Timeout gracefully at 10 seconds
- Show estimated data cost before any network request: "~50KB"
- Cache every response aggressively — never fetch the same data twice
- Three regions for launch: `east_africa`, `west_africa`, `southeast_asia`

---

## UI Flow & Layers

### Input
- **Primary:** Nothing — dongle always plugged in, app always monitoring
- **Secondary:** 4–6 large iconographic symptom buttons (no keyboard, no voice):
  - Won't start / Strange noise / Smells bad / Overheating / Runs rough / Check light new
- **Tertiary:** "Analyze Engine Bay" button (deliberate E4B vision query)

### Output layers (tap to expand)
```
Layer 1: LED color + haptic + buzzer tone       (zero literacy, zero screen)
Layer 2: Large status icon + 1 sentence         (read aloud automatically by TTS)
Layer 3: Plain language explanation + steps     (illustrated, numbered)
Layer 4: Raw DTCs + PIDs + confidence source    (mechanic mode toggle)
```

### Offline badge (always visible, top bar)
- Warm amber glow + "◈ LOCAL" when Gemma running on-device
- Token animation during inference (makes invisible compute visible)
- Shifts to cool blue "⟳ CLOUD" when connected and using cloud model
- Text: "Running on your device — no internet needed"

### Gemma handoff moment (low confidence or online-only feature)
Calm card slides up — never a red error:
> *"I can tell you what's likely wrong, but finding parts near you needs a connection. Connect to WiFi and I'll search for you."*

### TTS (text to speech)
- Primary: Piper TTS (open source, ~50MB per language, Apache 2.0)
  - Supports: Swahili, Hausa, English, French, Yoruba, Indonesian
- Fallback: Android built-in TextToSpeech API (always available)
- Default: every diagnostic read aloud automatically on opening
- Language detection: phone locale → system prompt → Gemma output language

---

## OBD Simulator (Dev + Demo)

### Dev (start today — zero hardware)
**Python ELM327-emulator over TCP:**
```bash
pip install ELM327-emulator
python -m elm -s car  # starts Toyota scenario
```
Android app connects over WiFi TCP instead of Bluetooth. One config change.
Scenario files: JSON defining DTCs + PIDs + VIN. Edit to create any scenario.

### Integration testing (Day 2–3)
**ELMulator library on ESP32:**
- Flash to same ESP32 used for LED control
- Bluetooth SPP — Android app can't tell it from real ELM327
- Switch scenarios via button or serial command

### Demo scenarios (script these precisely)

**Scenario A — All clear:**
```json
{
  "vin": "JTFBT22P100123456",
  "vehicle": "2008 Toyota Hilux 2KD-FTV",
  "dtcs": [],
  "pids": { "rpm": 800, "coolant_temp": 88, "ltft_b1": 3.2, "maf": 5.8 }
}
```
LED: slow green. App: large green icon. TTS: "Your vehicle is running normally."

**Scenario B — P0171 lean condition (main demo):**
```json
{
  "vin": "JTFBT22P100123456",
  "dtcs": ["P0171"],
  "pending_dtcs": ["P0101"],
  "freeze_frame": { "rpm": 820, "ltft": 22.7, "stft": -2.3, "maf": 3.4 },
  "pids": { "rpm": 780, "coolant_temp": 91, "stft_b1": -1.8, "ltft_b1": 22.7, "maf": 3.4 }
}
```
LED: amber pulse. App: yellow warning. TTS in Swahili: MAF cleaning recommendation.

**Scenario C — Critical overheating:**
```json
{
  "vin": "JTFBT22P100123456",
  "dtcs": ["P0217", "P0128"],
  "pids": { "rpm": 650, "coolant_temp": 118, "maf": 4.9 }
}
```
LED: urgent red. Buzzer: 3 beeps. TTS: "Stop driving immediately. Your engine is overheating."

**Scenario D — Story arc (demo video):**
Auto-sequence A → B → C over 60 seconds. LED shifts green → amber → red.
Cinematic. Narrate over the top.

### Physical mock port (stretch)
OBD-II female connector ($2) mounted on acrylic, wired to ESP32.
Label: "ANGAZA TEST PORT". Dongle plugs in. Looks like a real diagnostic bench.

---

## Android OBD Service (Core Code)

```kotlin
class OBDService {
    // Two BT connections managed separately
    val elmSocket: BluetoothSocket    // Classic BT to ELM327
    val esp32Gatt: BluetoothGatt      // BLE to ESP32 LED controller

    fun pollVehicleData(): VehicleState {
        return VehicleState(
            vin           = readVIN(),              // Mode 9 PID 02
            confirmedDTCs = readDTCs(mode = 0x03),
            pendingDTCs   = readDTCs(mode = 0x07),
            permanentDTCs = readDTCs(mode = 0x0A),
            liveData      = readPIDs()
        )
    }

    private fun readPIDs(): LiveData {
        return LiveData(
            rpm           = readPID("010C"),
            coolantTemp   = readPID("0105"),
            shortFuelTrim = readPID("0106"),  // STFT Bank 1
            longFuelTrim  = readPID("0107"),  // LTFT Bank 1
            mafRate       = readPID("0110"),
            throttle      = readPID("0111"),
            engineLoad    = readPID("0104")
        )
    }

    fun updateDongleLED(severity: Severity) {
        val command = when(severity) {
            CLEAR    -> """{"led":"green","pattern":"breathe"}"""
            WARNING  -> """{"led":"amber","pattern":"pulse"}"""
            FAULT    -> """{"led":"red","pattern":"urgent"}"""
            THINKING -> """{"led":"blue","pattern":"breathe"}"""
        }
        esp32Gatt.writeCharacteristic(ledCharacteristic, command)
    }
}
```

**Recommended library:** `pires/obd-java-api` on GitHub — handles ELM327 AT command handshake and basic PID parsing. Saves 2 days of low-level work.

---

## VIN Decode (Offline)

Mode 9 PID 02 (AT command: `0902`) returns 17-character VIN.

```
JTFBT22P100123456
│││││││││└──────── Sequential number
││││││││└───────── Check digit
│││││││└────────── Model year (1=2001, K=2019, M=2021...)
││││││└─────────── Plant code
│││││└──────────── Vehicle features / trim
│││└────────────── Vehicle line / model
│└──────────────── Manufacturer (WMI) + region
└───────────────── Country of origin
```

Characters 1–3 (WMI) → manufacturer. Character 10 → model year. Characters 4–8 → model/engine family.

Decode offline against bundled NHTSA vPIC database (free download, small JSON).

---

## DIY Repair Scope

### AI can reliably guide (covers ~65–70% of check engine lights)
Air filter, spark plugs, oil/filter, coolant top-up, battery terminals, fuse replacement, PCV valve, MAF sensor cleaning, vacuum hose inspection, O2 sensor location, brake pad visual inspection, belt tension check.

### AI cannot reliably guide (flag for mechanic)
Fuel system under pressure, brake hydraulics, transmission internals, head gasket, timing belt on interference engines, complex electrical diagnosis.

### The honest framing
Angaza does not replace a mechanic. It gives every owner:
1. Diagnostic knowledge to understand what's wrong
2. Confidence to fix what's DIY-safe
3. Information to avoid being exploited when mechanic is needed
4. Pre-trip health check before long drives
5. Maintenance schedule from odometer PID

---

## 5-Day Build Plan

### Today (Day 1)
- [ ] Python OBD emulator running, Android BT skeleton connecting
- [ ] Order ELM327 dongle (BAFX/Veepeak, classic BT)
- [ ] Order ESP32-C3 supermini + RGB LED + piezo + buck converter
- [ ] Start 3D print design (or source Hammond ABS box)
- [ ] Download Gemma 4 E2B weights to demo device
- [ ] Download generic DTC JSON from GitHub

### Day 2
- [ ] Android reads VIN + DTCs + key PIDs from emulator
- [ ] Basic health card UI renders (green/amber/red)
- [ ] ESP32 firmware: receives BLE JSON → drives LED pattern
- [ ] Generic DTC lookup integrated (P0171 → description)

### Day 3
- [ ] LiteRT-LM integrated, E2B returning structured output
- [ ] RAG retrieval wired (Toyota 2KD-FTV top 20 codes)
- [ ] Offline badge working, airplane mode tested end-to-end
- [ ] Piper TTS Swahili output for primary diagnostic sentence
- [ ] Enclosure assembled, LED soldered

### Day 4
- [ ] FastAPI backend deployed on Render
- [ ] Gemma function call fires to backend when WiFi available
- [ ] E4B vision attempt (stretch — fallback to cloud if too slow)
- [ ] VIN decode → vehicle profile → context enrichment
- [ ] Full LED states working end-to-end with Gemma output
- [ ] Airplane mode demo rehearsed

### Day 5
- [ ] Demo script rehearsed 3+ times
- [ ] Demo video recorded (real parking lot, real dongle if possible)
- [ ] Write-up: impact narrative + technical architecture + Haynes partnership pitch
- [ ] Submit before May 18 deadline

### Cut list (if time runs out, in order)
1. E4B vision → demo via cloud, mention in write-up as roadmap
2. Multilingual TTS → English only for demo, mention as roadmap
3. 3D printed case → Hammond ABS box or tape
4. Maintenance schedule feature → cut entirely
5. WhatsApp export → mention as roadmap

### Never cut
- Offline badge (makes Gemma's on-device role visible to judges)
- LED behavior (makes the product feel embedded)
- VIN decode (makes it feel automatic and intelligent)
- Gemma interpreting one DTC in airplane mode (core proof of concept)

---

## Hackathon Submission

**Competition:** Gemma 4 Good Hackathon — Kaggle
**Deadline:** May 18, 2026
**Requirement:** Must use Gemma 4 models
**Judged on:** Impact, technical execution, clear use case

### The demo money shot
Phone in airplane mode (show visibly) → plug dongle → VIN decodes silently → pending DTC appears → LED shifts green to blue (Gemma thinking) → 5 seconds → LED shifts to amber → Swahili plain-language diagnosis appears → TTS reads it aloud → illustrated MAF cleaning guide appears. Zero internet. Real intelligence. Real language.

### Partnership pitch (for write-up)
Haynes publishes repair manuals in 15 languages covering thousands of vehicles. A licensing partnership would give Angaza access to 100,000+ technical illustrations for vehicle-specific repair guidance — reaching markets Haynes has never served. The app is the distribution vehicle for their content in the developing world.

### The one-sentence pitch
> *"Angaza gives every vehicle owner the diagnostic intelligence of a dealer mechanic — offline, in their language, powered by Gemma 4 running on their phone — so that a broken-down matatu driver in Nairobi can fix his vehicle, keep his income, and get home."*

---

*Last updated: May 2026*
