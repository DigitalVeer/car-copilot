# Demo Video — Shot List (lunch shoot)

Goal: capture ~75s of raw footage in 20-25 min. We'll trim it into a 3-min final with voiceover and B-roll after.

## Pre-flight (2 min)

- **Pixel 9, airplane mode ON.** Visible in the status bar — this is the on-device proof.
- Battery >50%, brightness ~80%, **Do Not Disturb ON** (no notifications in shot).
- Cold-launch the app once to confirm the model loaded, then force-close. Re-open fresh on camera.
- Use the Pixel's built-in **Screen Record** (Quick Settings → Screen record). Much cleaner than filming the screen with another camera. Turn ON "Record audio" → Device audio (in case you want token-stream sounds later).
- Steady the phone: lay it flat on a dark surface or use a stand. **No handheld for screen-recorded segments.**
- One physical-camera clip you'll want: phone in hand or on a dashboard, ideally with a real check-engine light visible somewhere. This is the establishing shot.

## Shots to capture

| # | Source | Length | Action |
|---|---|---|---|
| 1 | Real camera | 5-8s | Dashboard with check-engine light on. Slow push-in. Establishing shot. Can be a parked car. |
| 2 | Real camera | 4-6s | Phone in hand near the dashboard or in driver's seat. OBD dongle visible if possible. |
| 3 | Screen record | 5s | Cold-launch from Home screen. Pause on Home screen with airplane icon visible top-right. |
| 4 | Screen record | 14s | Tap "Scan" / fixture trigger → **Issue screen appears → AI strip streams**. Stay on this until the strip finishes. The streaming is the proof. |
| 5 | Screen record | 8s | On Issue page: scroll down to show DTC code badge and supporting signals. Tap "Walk me through the fix". |
| 6 | Screen record | 20s | Walkthrough plan loads (this takes a moment — let it). Step 1 streams. **Linger on the SpecsChipRow** (torque/gap values pinned beside the body — this is the safety story visually). Tap "Next step". Let step 2 stream briefly. |
| 7 | Screen record | 10s | Back to Issue → Mechanic Draft → text streams. |
| 8 | Screen record | 8s | Bottom-nav to History → pattern explanation streams. |
| 9 | Real camera | 4s | Closing shot: phone resting on the dashboard, calm. |

**Total raw: ~80s.** We trim to ~150s of A-roll in the final, padded with title/diagram inserts to hit 3:00.

## Discipline while shooting

- **Don't rush taps.** Give Gemma 1-2 seconds to start streaming before cutting away. The ThinkingDots animation is good — it sells "really running locally."
- Shoot each screen segment **2-3 times** with slight variation. You'll thank yourself in editing.
- After each Screen Record, **play it back** before moving on. Recording fails silently more often than you'd think.
- Keep one "no taps" clip of each screen — just the screen sitting still, content visible, no animation. Useful as a "freeze" for the voiceover.

## Bonus shots if time permits

- **`adb logcat -s CarCopilot:V` running** on a laptop screen, showing `bench surface=...` lines scrolling past. Hard proof of measurement. 4-6s clip.
- The repo open in IDE on the SpecsChipRow code or RulesEngine code. Pan slowly. Sells "real engineering."
- Slow zoom on the model file path on the phone (Settings → Storage → app data) or on the Hugging Face page for the `.litertlm`. Optional.

## Files to bring back

Copy from phone to laptop via USB or Files app:
- `/Movies/Screen recordings/Screen_Recording_*.mp4`
- Anything you shot with the real camera

Drop them in `submission/footage/` (I'll add this to `.gitignore`).

## Voiceover

Record the voiceover **after** you have footage assembled in a rough cut. It's much easier to talk to picture than to time picture to voice. We'll have a 380-410 word script ready when you're back — I'll size it to your actual A-roll length.

---

Reply when you're back. I'll have the writeup draft and the public README intro ready, and we'll cut the video together.
