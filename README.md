# CAR·COPILOT — Android setup

CAR·COPILOT is an on-device car diagnostic copilot: a two-screen Android demo that takes a car's OBD-II fault data, runs Gemma 4 E4B locally via LiteRT-LM, and streams a friend-on-the-phone explanation of what's wrong and what to do about it. No cloud calls, no location services, no telemetry. The full design context lives in `CLAUDE.md` and `reference/`; this file just gets you to a working APK.

## Prerequisites

- **Android Studio** Iguana (2023.2.1) or newer. Anything that bundles a recent AGP 8.x and JDK 21+ works.
- **JDK 21+.** Android Studio ships its own JBR — point Gradle at it (Settings → Build → Build Tools → Gradle → Gradle JDK), or set `JAVA_HOME` to that JBR path if you build from the command line.
- **ADB on `PATH`.** Standard install location is `%LOCALAPPDATA%\Android\Sdk\platform-tools\` on Windows or `~/Library/Android/sdk/platform-tools/` on macOS.
- **A physical Android 8.0+ device with USB debugging authorized.** The reference target is a Pixel 9; any Tensor or Snapdragon-8-class device with an OpenCL-capable GPU should work. Emulators won't — the GPU delegate needs a real driver.

## Get the source

```bash
git clone <repo-url> CarCopilot
cd CarCopilot
```

Open the project root in Android Studio (`File → Open…`, select the `CarCopilot` directory). Wait for the Gradle sync to finish. The first sync downloads Compose BOM, LiteRT-LM, and the Kotlin toolchain — give it a few minutes on a fresh machine.

## Get the model

The app needs **`gemma-4-E4B-it.litertlm`** (~3.41 GB). It is not in git.

1. Download it from the [litert-community Hugging Face repo](https://huggingface.co/litert-community). Get the **`.litertlm`** file, *not* the `.task` file — the `.task` files in the same repos are the **web** build and will not load via the Android SDK.
2. Push it to the device's shared tmp directory once:

   ```bash
   adb push gemma-4-E4B-it.litertlm /data/local/tmp/
   ```

   This is the only world-readable location ADB can write to on a stock, non-rooted device. It's the staging source the app reads on first launch.
3. On first launch, `CarCopilotApp.onCreate` constructs `GemmaService`, which copies the model from `/data/local/tmp/` into the app's private `filesDir` (the GPU delegate writes a sidecar weights cache next to the model file, and `/data/local/tmp/` is not writable by the app's UID). Subsequent launches reuse the `filesDir` copy — you can delete the `/data/local/tmp/` copy after the first run to reclaim the ~3.41 GB.

## Build and install

From the project root:

```bash
# Build the debug APK
./gradlew assembleDebug

# Install on the connected device (replaces in place)
adb install -r -d app/build/outputs/apk/debug/app-debug.apk

# Tail runtime logs (project-tagged messages only)
adb logcat -s CarCopilot:V

# Clean reinstall — wipes filesDir, forces a fresh model copy on next launch
adb uninstall com.example.carcopilot

# Confirm the device is visible if `adb install` complains
adb devices
```

The first launch after install copies the model (~10-20s) and warms the GPU shader cache (~30-60s). The engine starts warming in `CarCopilotApp.onCreate` while the user sits on the Home screen, so by the time they tap into the Issue page the prefill is fast.

## Troubleshooting

**`adb devices` lists your device as `unauthorized`.** Unplug, replug, and accept the RSA-key prompt on the device screen. If the prompt doesn't appear, toggle USB Debugging off and back on in Developer Options.

**`Model missing at /data/local/tmp/gemma-4-E4B-it.litertlm. Run: adb push ...`** — exactly what it says. The first launch couldn't find the staging source. Push the model to `/data/local/tmp/` and relaunch. If the app crashed mid-copy on a previous attempt, also run `adb uninstall com.example.carcopilot` first to clear the half-written `filesDir` copy.

**App launches but logs `engine init failed`.** Check `adb logcat -s CarCopilot:V` for the underlying exception. The most common cause is the GPU delegate failing to dlopen the vendor OpenCL driver. The two `<uses-native-library>` entries in `app/src/main/AndroidManifest.xml` (`libvndksupport.so` and `libOpenCL.so`, both `required="false"`) authorize that dlopen — if you've modified the manifest, confirm those lines are still present. The app fails soft to a canned synthesis if init fails, so the UI still renders; only the live streaming is lost.

**Build fails with `JAVA_HOME is not set`.** Either run builds from inside Android Studio (which uses the bundled JBR), or set `JAVA_HOME` to the JBR directory (e.g. `C:\Program Files\Android\Android Studio\jbr` on Windows) before invoking `./gradlew`.

**Sideloading by drag-and-drop in the device's APK browser is blocked.** Corporate-managed Pixels often block unknown-source installs through the UI but still permit ADB installs. Use `adb install -r -d` rather than emailing the APK to the device.

## Where to look next

- `CLAUDE.md` — project guardrails, architecture, current state, what's in-scope and out-of-scope.
- `FUTURE_WORK.md` — known issues and the post-demo backlog, grouped by category.
- `reference/carcopilot_mockup_v09.html` — design source of truth; open in a browser side-by-side with the running app.
- `reference/prompts/*.md` — voice rules (`system.md`) and per-surface prompt templates; mirrored into `app/src/main/assets/`.
- `reference/carcopilot_android_spec.md`, `reference/carcopilot_phase_6_polish.md`, `reference/carcopilot_design.md` — historical / mixed reference docs. Each carries a STATUS banner at the top describing what's still live vs. archaeology. Don't treat them as current specs.
