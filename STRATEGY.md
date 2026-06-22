# CAR·COPILOT — Cloud Product Strategy

> STATUS: **proposal, 2026-06-22.** This document is a strategy artifact, not a
> shipped decision. It deliberately contemplates crossing the hackathon's
> central "no cloud calls" constraint (see `CLAUDE.md` → Hard constraints). The
> on-device LiteRT-LM build remains the source of truth for the demo; nothing
> here changes the shipped app. The thin-slice scaffold in `server/` and
> `inference/CloudInferenceService.kt` is an isolated proof of concept, not
> wired into `MainActivity`.

## The honest reframe

Today the product's identity *is* the constraint: all inference on-device, no
cloud, no location. That won the hackathon. The moment we go cloud, that moat
evaporates — we stop competing on the privacy gimmick and start competing on the
actual product: diagnosis quality, the mechanic handoff, the experience.

So the cloud version is not "the same app with the model in the cloud." It's a
*more capable* product that shares the deterministic core and the voice. The
reason to go cloud is to do things on-device Gemma 4 fundamentally cannot — and
`FUTURE_WORK.md` is already a list of things the local constraint blocks:

- **Prefill dominates wall-clock on every surface** (~6s to first token, ~30s
  end-to-end synthesis). A frontier API with prompt caching cuts that to ~1–2s.
- **Numeric hallucination** ("30 Nm" → "0 Nm", DTC drift) that we papered over
  with `SpecsChipRow`. A frontier model + tool use kills this class of bug.
- **No real history, no vector RAG, no recall/TSB/parts data** — all want a server.
- **Single-Conversation-per-Engine SIGSEGV** churn — gone entirely; the cloud
  has no such constraint.

## What stays vs. what moves

The best architectural instinct is already in the repo: **Gemma never
classifies; `DTCTable` is the source of truth.** Keep that. The deterministic
layer is the safety guarantee and the differentiation against "just paste your
code into ChatGPT."

| Layer | Cloud decision |
|---|---|
| `RulesEngine` + `DTCTable` classification | Keep deterministic, **move to server** so the catalog updates without an app release |
| `RagStore` (exact-match today) | **Upgrade to real vector retrieval** over a larger corpus (TSBs, recalls, forum-distilled fixes) — the data moat |
| 5 narrative surfaces (synthesis, mechanic draft, history pattern, walkthrough plan/step) | **Move to a frontier model API** with the *exact same prompts* — `reference/prompts/` voice is the asset, ported verbatim |
| `OBDDataSource` (BLE/TCP/fixture) | **Stays on-device** — the car connection is inherently local; the app captures the snapshot and POSTs it |
| History | Becomes a real server-side `HistoryRepository` — per-VIN, cross-device, the basis for "your car over time" |

The app keeps OBD capture + Compose UI; the brains move server-side.

## Frontier model recommendation

Build on **Claude**, split by job rather than picking one model:

- **Claude Sonnet 4.6** (`claude-sonnet-4-6`) — the workhorse for the
  high-volume narrative surfaces. Fast, cheap enough to run on every diagnosis,
  more than smart enough for two grounded paragraphs in a fixed voice.
- **Claude Opus 4.8** (`claude-opus-4-8`) — the "senior mechanic" tier for hard
  cases: agentic diagnosis that reasons across live readings + history +
  retrieved TSBs and calls tools (parts pricing, recall lookup, VIN decode).
  Invoke when confidence is low or the user asks a follow-up.
- **Vision** (both models) — a capability local Gemma can't touch cheaply:
  photograph the dashboard cluster, the engine bay, the leak under the car, and
  have the model read it. A genuinely new product surface, not a port.

Three things map directly onto problems in `FUTURE_WORK.md`:

1. **Prompt caching** — the single biggest measured cost is prefill of the
   system prompt + DTC context + RAG. Cache that prefix and you pay it once per
   session, not once per surface. This is the cloud answer to the entire
   "Performance" section. (The thin-slice backend already sets `cache_control`
   on the system prompt.)
2. **Tool use** — define tools for parts pricing, recall/TSB lookup,
   torque-spec retrieval. The model *fetches* the canonical "30 Nm" instead of
   generating it, which kills the numeric-hallucination class.
3. **Multi-turn chat** — "Ask a follow-up" becomes trivial server-side; no
   KV-cache eviction hacks.

### Pricing reference (per 1M tokens, pulled 2026-06)

| Model | ID | Context | Input | Output |
|---|---|---|---|---|
| Claude Opus 4.8 | `claude-opus-4-8` | 1M | $5.00 | $25.00 |
| Claude Sonnet 4.6 | `claude-sonnet-4-6` | 1M | $3.00 | $15.00 |
| Claude Haiku 4.5 | `claude-haiku-4-5` | 200K | $1.00 | $5.00 |

- **Prompt caching:** cache *write* costs 1.25× input (5-min TTL) or 2× (1-hour);
  cache *read* costs 0.1× input. On Sonnet 4.6 that's a $0.30/MTok read vs. a
  $3.00/MTok cold prefill — a 10× saving on the cached prefix.
- **Vision:** images are billed as input tokens (no separate vision line item);
  cost scales with resolution. Budget a dashboard photo at roughly 1–2K input
  tokens.

### Per-diagnosis cost model

Grounded in the repo's own measured token counts (`FUTURE_WORK.md`: synthesis
prompt ~1,449 tok, walkthrough step ~2,100 tok). A **full diagnosis** = all five
surfaces plus ~6 walkthrough steps ≈ 10 Claude calls, ~18K input + ~2.5K output
tokens. A **light diagnosis** = synthesis only (~1.65K in + ~0.2K out).

| Scenario | Model | Cost / diagnosis (no cache) | With prompt caching* |
|---|---|---|---|
| Light (synthesis only) | Sonnet 4.6 | ~$0.008 | ~$0.006 |
| Light (synthesis only) | Opus 4.8 | ~$0.013 | ~$0.010 |
| Full (5 surfaces + 6 steps) | Haiku 4.5 | ~$0.03 | ~$0.02 |
| Full (5 surfaces + 6 steps) | Sonnet 4.6 | ~$0.09 | ~$0.06 |
| Full (5 surfaces + 6 steps) | Opus 4.8 | ~$0.15 | ~$0.11 |

\* Caching assumes a shared ~1.5K-token prefix (system + DTC context + RAG)
read across the session's calls at 0.1×; ~30% input saving on the full path.

**Takeaway:** even at the Opus full-path ceiling (~$0.11/diagnosis), unit
economics are healthy against a freemium subscription. Default the high-volume
surfaces to Sonnet 4.6 (~$0.06 full) and reserve Opus 4.8 for the agentic
follow-up tier. Refine these with the `count_tokens` API against real prompts
before committing pricing.

## Customer journey — pick the wedge

The repo already tells us the strongest wedge: the **Mechanic Draft** surface —
the bridge between "scared driver with a check-engine light" and "doesn't get
ripped off at the shop." Two viable ICPs:

**A. B2C driver (the current app's user).** Budget-conscious, older car.
1. Plug in a ~$20 BLE OBD dongle → app captures snapshot (stays local).
2. Server classifies + a frontier model explains in the friend-on-the-phone
   voice, *with real parts pricing and any open recalls for their VIN*.
3. "Should I drive it?" verdict + DIY walkthrough *or* a mechanic handoff note
   that quotes a fair price range.
4. History persists per-car: "this is the third time this month" becomes real.
- Monetize: freemium (one diagnosis free; subscription for unlimited + history +
  recall monitoring).

**B. Independent shops / fleets (B2B).** Same engine, different skin: a shop runs
a snapshot, gets an instant plain-English writeup to hand the customer, plus
TSB/recall surfacing. This is where retention and money are — fleets have many
vehicles, recurring need, per-seat pricing.

**Recommendation:** build B2C for the demo-able story and distribution, but
design the backend so the B2B/fleet dashboard is the same API with a different
client. The accumulated data (anonymized DTC → real-fix → real-cost outcomes) is
a moat neither a generic chatbot nor an OEM tool has.

## What to build first (thin slice)

1. A backend exposing `POST /diagnose` that takes the existing `OBDSnapshot`
   JSON, runs the *ported* deterministic classifier, and streams synthesis from
   Claude using the existing prompts. **→ scaffolded in `server/`.**
2. Swap the app's `GemmaService` for a `CloudInferenceService` behind the same
   streaming interface — the `LaunchedEffect` collectors and `SynthesisState`
   extractors barely change because the JSON contract is identical.
   **→ scaffolded in `inference/CloudInferenceService.kt`.**
3. Keep `GemmaService` as an **offline fallback** — a *feature*: "works in a dead
   zone, gets smarter on signal." Turns the local heritage from sunk cost into a
   hybrid edge/cloud differentiator.
4. Add prompt caching (done in the scaffold) + one tool (recall lookup by VIN) to
   prove the new-capability story.

## The one risk to name

**Liability.** The moment a cloud service tells someone "safe to drive" and it
isn't, we own that in a way an on-device toy didn't. The deterministic
`DTCTable`-owns-severity rule is the friend here: keep the model strictly on
narrative, keep the *verdict* deterministic and auditable, and version every
classification decision server-side.
