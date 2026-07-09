# CAR·COPILOT → Consumer Product Spec

> **STATUS: STRATEGY.** Competitive product brief for turning the hackathon copilot into a FIXD-class consumer business. Not a build plan for the current Android/on-device tree. The live app remains the technical proof; this document is the commercial pivot.

**Working name:** CAR·COPILOT (keep for now; brand rename is a launch decision)  
**Competitive target:** FIXD (sensor + app + Premium subscription)  
**North-star positioning:** The AI Primitive for your car — not another code reader.

---

## 1. The opportunity in one paragraph

FIXD won the “translate the check engine light” category with a cheap Bluetooth dongle, plain-English codes, and a Premium upsell (mechanic hotline, cost estimates, emissions). The category is now stale: the hardware looks like a commodity ELM327, the app feels like a utility, and “AI Mechanic” is bolted on as chat. CAR·COPILOT already has the hard product kernel FIXD never built well — deterministic diagnosis + friend-on-the-phone voice + DIY walkthrough + mechanic draft. The commercial move is to wrap that kernel in **designed hardware**, an **iOS-first cloud app**, and a **Rabbit-style conceptual campaign** that reframes car care as an AI primitive, not a scanner SKU.

---

## 2. Competitive teardown: FIXD

| Dimension | FIXD today | Opening for us |
|---|---|---|
| Hardware | Generic black OBD puck, ~$20 promo / $60 MSRP | Object people want to leave plugged in and show friends |
| Core promise | “Codes in plain English” | “A friend who knows cars — and gets you to a fix” |
| Diagnosis depth | Code library + severity + likely repair | Rules engine + live PIDs + RAG + confidence (already in this repo) |
| AI | Premium “AI Mechanic” chat bolt-on | AI is the product surface, not a chat tab |
| DIY | Thin / secondary | First-class walkthrough with torque/gap chips |
| Mechanic handoff | Hotline (human) | Pre-drafted shop note the user can send |
| Platform | iOS + Android, utility UI | iOS-first, Apple-HIG clean, brand-led |
| Monetization | Sensor loss-leader → $100/yr Premium | Hardware margin + optional Copilot+; avoid dark-pattern trials |
| Trust | Mixed (subscription surprise, “ripped off by mechanic” tone) | Calm, concrete, no scare tactics |

**What we do not copy:** clear-DTC as a hero feature, fear-based “don’t get ripped off” copy, weekly subscription SKUs, location-gated features, generic scanner aesthetics.

**What we keep from this repo:** Issue schema, DTC table + RulesEngine, voice rules (`system.md`), walkthrough + mechanic-draft surfaces, fail-soft fallbacks, no Mode 04.

---

## 3. Product thesis

### Positioning line
**CAR·COPILOT is the AI Primitive for car care.**  
Plug in once. When something’s wrong, it tells you what it is, how bad it is, what to do, and what to say to a shop — in the voice of a friend who knows cars.

### Conceptual name (Rabbit parallel)
Rabbit sold **LAM / Large Action Model** — a named primitive that made the hardware feel inevitable.  
We sell **Vehicle Intent Model (VIM)** — or shorter in marketing: **the Car Primitive**.

| Rabbit | Us |
|---|---|
| Large Action Model (LAM) | Vehicle Intent Model (VIM) / “Car Primitive” |
| Pocket companion that *acts* | Glovebox companion that *decides and drafts* |
| Push-to-talk as familiar metaphor | Plug-and-forget OBD as familiar metaphor |
| Teenage Engineering object | Designed dongle as desk/glovebox object |
| “Intention over interaction” | “Diagnosis over codes” |
| App-free OS fantasy | Phone-native, but AI-first (not scanner-first) |

The campaign sells the **primitive**, not the feature list. Hardware is the vessel. The app is the interface. The primitive is the story.

### Category we create
Not “OBD2 scanner.” Not “mechanic hotline.”  
**Personal car intelligence** — a always-there layer between the ECU and the human.

---

## 4. Hardware: the cool OBD

### Design brief
Make a dongle people leave in the port *and* leave on a desk. FIXD’s sensor disappears into “cheap Bluetooth gadget.” Ours should read as **industrial design**, not accessory.

**Form principles**
- One silhouette, recognizable at 10 feet (Rabbit’s orange square test).
- Soft-touch shell + one accent material (anodized aluminum ring or ceramic-feel polymer) — not glossy black ABS.
- Status language via a single LED (healthy / attention / talking) — no RGB party mode.
- Optional: magnetic desk cradle so the object lives outside the car too (unboxing ritual + brand presence).
- Size: flush enough not to kick shins; proud enough to feel intentional.

**Industrial direction (pick one and commit)**
1. **Tool-object** — brushed metal, Jeep/Leatherman honesty, “belongs in a garage.”
2. **Companion-object** — warm polymer, rounded, “belongs next to AirPods.”  
Recommend **2 for consumer launch**, with a later Pro SKU in direction 1.

**Technical requirements (v1)**
- BLE to iPhone (Core Bluetooth); Classic SPP only if needed for legacy ELM — prefer a custom BLE firmware profile.
- ELM327-compatible command set *or* custom MCU speaking the same PID/DTC surface we already model in `OBDSnapshot`.
- Always-on monitoring mode (ignition-aware): wake on engine start, quiet sleep, phone notify on new DTC / severity change.
- No GPS on the dongle. No microphone. Trust is a feature.
- Firmware OTA via the app.
- Certifications: FCC/CE, vehicle electrical safety, temperature range for cabin extremes.

**SKU ladder**
| SKU | Price band | Role |
|---|---|---|
| Copilot Sensor | $79–$99 | Hero consumer object + free app tier |
| Copilot Sensor + Desk Cradle | $119 | Gift / brand object |
| Copilot Pro (fleet / diesel / multi-protocol) | $149–$199 | Later; deeper PIDs, multi-vehicle |

**Manufacturing note:** Start with a white-label ELM327 module inside a custom shell for MVP hardware, then move to custom MCU once volume justifies. The *object* ships first; the silicon can catch up.

---

## 5. iOS app: very clean, no on-device LLM

### Strategic pivot from this repo
| Today (hackathon) | Consumer product |
|---|---|
| Android + LiteRT-LM on device | **iOS-first**, cloud LLM |
| No cloud AI | Cloud inference with strict data minimization |
| Gemma local | Claude / GPT-class API behind our prompts |
| Five Compose screens | Same five surfaces, SwiftUI, refined |
| Fixture / BLE seams | Real BLE sensor as default path |

On-device LLM was the right hackathon claim. For a FIXD competitor it is the wrong commercial claim: App Store users expect instant answers, iPhones vary wildly on NPU headroom, and a 3+ GB model is a support nightmare. **Keep the prompts, schema, and fail-soft behavior; move the model to the cloud.**

### App principles
1. **One composition per screen** — brand + one job. No dashboard soup.
2. **Issue is the UI contract** — port `Schema.kt` → Swift models; screens still render an `Issue`.
3. **Gemma never classifies** → **Cloud LLM never classifies.** Rules engine + DTC table remain source of truth for severity, route, cost, time.
4. **Friend voice** — ship `system.md` rules unchanged into the server prompt pack.
5. **Fail soft** — canned fallbacks if the API fails; never a blank error wall.
6. **No Mode 04** in v1 consumer (or bury behind explicit “I understand” if legal/support demands it later).
7. **No location permission** for core flows. Artifacts that need a place use the literal “my current location” pattern from the current product.

### Information architecture (keep the five surfaces)
```
Home          → car health pulse + last scan + connect sensor
Issue         → severity, title, AI synthesis strip, next action
Walkthrough   → step plan + per-step body + specs chips
Mechanic Draft→ editable shop note, share sheet
History       → pattern explanation over past issues
```

### Visual direction
- Light, Apple-HIG-adjacent; reuse the v10 light token discipline (indigo action, two-tone semantic fills vs inline text).
- Expressive type (keep Geist + JetBrains Mono spirit, or SF Pro + a single display face that isn’t Inter).
- Motion: 2–3 intentional moments — sensor connect confirmation, synthesis stream-in, walkthrough step advance. No confetti.
- Avoid FIXD’s dense utility chrome and Rabbit’s toy maximalism — aim for **calm instrument**.

### Cloud AI architecture (high level)
```
iPhone (BLE) → Sensor → OBDSnapshot
                ↓
         RulesEngine + DTCTable + RagStore   (can run on-device or edge)
                ↓
         Classification + Issue skeleton
                ↓
         API: /synthesize, /walkthrough/plan, /walkthrough/step, /mechanic-draft, /history-pattern
                ↓
         Prompt pack (assets/*.md) + streaming tokens → SwiftUI collectors
```

- Streaming SSE/WebSocket for the same token-delta UX we already have.
- Prompt templates stay the product moat; model vendor is swappable.
- PII policy: send snapshot + classification + RAG snippets only — no contacts, no precise location, no raw VIN in logs if avoidable (hash or last-8 only).

### Platform sequencing
1. **iOS 1.0** — sensor pairing, scan, five surfaces, Share Sheet for mechanic draft.
2. **iOS 1.1** — push on new DTC, maintenance reminders, multi-car profiles.
3. **Android 2.0** — port after iOS brand and hardware are proven (reuse Kotlin rules/RAG; new UI).

---

## 6. “AI Primitive” marketing campaign (Rabbit playbook, applied)

### Campaign name
**Introducing the Car Primitive**  
Subtitle: *Diagnosis over codes.*

### Three Rabbit moves, remapped

**1. Leverage the familiar as new**  
Everyone knows the OBD port under the dash. Reframe it: that port was always a USB for your car’s brain — we finally plugged something intelligent into it. Demo gesture: plug in → phone lights up with a calm Issue card in <10s. No menus.

**2. Conceptual naming for storytelling**  
Lead every keynote, landing page, and press kit with **Vehicle Intent Model / Car Primitive** — not “our app uses GPT.” Show one diagram: ECU signals → Intent → Action (DIY steps or shop draft). Feature lists come second.

**3. Ignite curiosity with open possibilities**  
Launch with 3 hero scenarios (reuse our fixtures as filmable truth):
- Misfire on a daily driver → $45 coil, 30 minutes, walkthrough
- Fuel rail pressure on a diesel → “don’t keep driving,” mechanic draft
- Recurring pattern across months → History says “this keeps coming back after short trips”

Tease a future “teach your Copilot” / garage community without over-promising.

### Launch narrative (90-second keynote arc)
1. Check engine light is a panic primitive from the 90s.
2. Scanners gave you codes. FIXD gave you English. Still not a decision.
3. We built a Vehicle Intent Model: structure from the car, voice from a friend, actions you can take.
4. Hardware reveal (object on stage / desk cradle).
5. Live demo: plug → Issue → Walkthrough step 1 → share Mechanic Draft.
6. Price + ship date. No subscription trap in the hero CTA.

### Brand system
- Wordmark: CAR·COPILOT (middot stays — it’s already distinctive).
- Campaign lockup: “Car Primitive” as the idea; product name as the thing you buy.
- Film language: cabin light, hands, port, phone — no stock “happy family in SUV.”
- Avoid purple-glow AI clichés; stay in the warm instrument palette.

### Channels
- Launch film + product site (full-bleed hardware hero, one CTA: Pre-order).
- CES / SEMA optional; better: a single city garage pop-up + creator cars.
- YouTube/TikTok: “friend explains my check engine light” series using real scans (permissioned).
- Press: Wired / The Verge / Jalopnik — design story + anti-subscription honesty.

### Trust campaign (anti-FIXD)
Lead with: **Sensor works without paying us forever.** Copilot+ is optional depth, not a hostage situation. Publish pricing on the box.

---

## 7. Business model

| Layer | Price | Includes |
|---|---|---|
| Hardware | $89 MSRP (launch $69) | Sensor, free app, unlimited scans, Issue + severity + basic synthesis |
| Copilot+ | $79/year or $8/month | Full walkthroughs, mechanic drafts, history patterns, priority model, multi-car |
| Human assist (later) | Add-on or Plus tier | ASE chat/voice — only after AI draft quality is trusted |

**Unit economics target (directional):** Hardware COGS <$25 at volume; Plus attach >25% by month 6; support cost controlled by fail-soft + clear DIY vs shop routing.

**What we refuse:** auto-enrolling annual trials buried in checkout; weekly SKUs; paywalling the meaning of a code.

---

## 8. What transfers from this codebase

**Ship as-is (conceptually):**
- Voice pack (`reference/prompts/` + assets prompts with `{rag_context}` etc.)
- `Issue` / `Classification` / `OBDSnapshot` contracts
- `RulesEngine` + `DTCTable` + thin catalog + RAG docs
- Walkthrough phase splitting + specs chips idea
- Five-surface IA and fail-soft philosophy
- ELM327 protocol knowledge + emulator for hardware bring-up

**Do not transfer as product constraints:**
- On-device LiteRT-LM / Gemma packaging
- Android-only delivery
- “No cloud API calls” as a consumer claim (replace with “we don’t sell your drives” + minimization)
- Hackathon performance workarounds (surface mutex, etc.)

**Rebuild:**
- SwiftUI app shell
- Cloud prompt service + streaming
- Custom hardware + BLE pairing UX
- Account, purchase, OTA, support

---

## 9. Phased roadmap (technical depth, not calendar)

### Phase A — Narrative & design freeze
- Lock positioning (“Car Primitive”), hardware ID direction, iOS visual system.
- Produce launch film storyboard + landing page copy.
- Legal: privacy policy for cloud inference; subscription disclosures.

### Phase B — Cloud kernel
- Port RulesEngine + DTC/RAG to a shared Kotlin Multiplatform or TypeScript service (or keep Kotlin backend).
- Stand up streaming endpoints that consume the existing prompt files.
- Golden-output eval harness against misfire / hilux fixtures (voice regression).

### Phase C — iOS MVP (software-only)
- SwiftUI five screens against fixture snapshots + Wi-Fi/TCP emulator (same scenarios).
- Sign in with Apple, StoreKit for Copilot+.
- Share Sheet mechanic draft.

### Phase D — Hardware MVP
- Custom shell + qualified BLE module; desk cradle optional.
- Pairing flow, ignition-aware monitoring, OTA.
- Field test matrix: 1996+ gas, 2008+ diesel; top 20 US makes.

### Phase E — Launch
- Pre-order hardware + free app.
- Copilot+ optional from day one, clearly labeled.
- Jalopnik/Verge story + garage demo day.

### Phase F — Moat
- Expand deep DTC entries + walkthrough markdown library.
- Pattern detection on real longitudinal scans.
- Android port; Pro hardware SKU.

---

## 10. Risks and non-goals

**Risks**
- Hardware makes us a supply-chain company; mitigate with white-label guts + custom shell first.
- Cloud AI cost at free-tier abuse; mitigate with fair-use caps + on-device classification so free tier can stay useful offline for severity.
- FIXD price war at $20; do not compete on price — compete on object + intent + trust.
- Rabbit-style hype without delivery; only demo live paths we can run on stage.

**Non-goals (v1)**
- Clearing codes as a marketing feature
- Location-based shop marketplace
- Full shop OS / parts ecommerce
- On-device LLM on iPhone
- Spanish localization (later)
- Being “ChatGPT for cars” without structured diagnosis

---

## 11. Success criteria

| Signal | Meaning we’re winning |
|---|---|
| Unboxing / desk photos organic | Hardware is a brand object, not a puck |
| Press uses “Car Primitive” / VIM language | Conceptual naming landed |
| Time-to-understanding < 30s after plug | Beats FIXD’s “scan then dig” |
| DIY completion or draft-sent rate | AI surfaces drive action, not just reading |
| Plus attach without refund spikes | Monetization without FIXD-style backlash |
| NPS / “explained like a friend” verbatim | Voice moat holds |

---

## 12. One-page summary for investors / partners

**Problem:** Check engine lights still create panic; FIXD made codes readable but not decisive, and the category looks like a $20 Bluetooth tax with a subscription trap.

**Product:** A designed OBD sensor + clean iOS app powered by a Vehicle Intent Model — deterministic automotive structure with a friend-voice AI layer that routes you to DIY steps or a shop-ready draft.

**Why us:** We already built the diagnosis kernel, voice system, and action surfaces. The commercial product is packaging, hardware, iOS, and a campaign that sells a primitive — not another scanner.

**Ask shape (when relevant):** Hardware tooling + first production run, cloud inference runway, and brand launch — not another hackathon model port.
