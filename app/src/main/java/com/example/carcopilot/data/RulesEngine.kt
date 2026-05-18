package com.example.carcopilot.data

import com.example.carcopilot.model.Classification
import com.example.carcopilot.model.Confidence
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.Route
import com.example.carcopilot.model.Severity

/**
 * Deterministic rules engine: [OBDSnapshot] → [Classification].
 *
 * Inspects named live readings against DTC-specific thresholds to assign a
 * confidence level, a likely cause in plain language, and a list of
 * human-readable signals that support the conclusion. These feed directly into
 * [com.example.carcopilot.inference.PromptBuilder.renderSynthesisPrompt] so
 * Gemma can reference real evidence rather than generating plausible-sounding
 * guesses.
 *
 * Keys used here must match [com.example.carcopilot.data.PidSpec.display]
 * values in Elm327Protocol — that's where live readings get their names.
 */
object RulesEngine {

    fun classify(snapshot: OBDSnapshot): Classification {
        val code = snapshot.dtcs.firstOrNull()?.code ?: return noSignal()
        val readings = snapshot.liveReadings.associateBy { it.key }
        val result = when {
            code == "P0087" || code == "P1229"       -> classifyFuelRail(code, readings)
            code == "P0171"                           -> classifyLean(readings)
            code == "P0300"                           -> classifyRandomMisfire(readings)
            code.matches(Regex("P030[1-8]"))          -> classifyMisfire(code, readings)
            code == "P0507"                           -> classifyIdleHigh(readings)
            code.matches(Regex("P067[0-9]"))          -> classifyGlowPlug(code, readings)
            else                                      -> noSignal(code)
        }
        return result.copy(engineFamily = snapshot.engineFamily)
    }

    private fun classifyFuelRail(
        code: String,
        readings: Map<String, LiveReading>,
    ): Classification {
        val railKpa = readings["Fuel rail pressure"]?.value?.toDoubleOrNull()
        val signals = mutableListOf<String>()

        val confidence = when {
            railKpa != null && railKpa < 20_000 -> {
                signals += "Fuel rail at ${railKpa.toInt()} kPa — critically below the 34,500 kPa idle minimum; engine will lose power under load"
                Confidence.HIGH
            }
            railKpa != null && railKpa < 30_000 -> {
                signals += "Fuel rail at ${railKpa.toInt()} kPa — below the 34,500 kPa idle threshold; consistent with a clogged filter reducing flow"
                Confidence.HIGH
            }
            railKpa != null -> {
                signals += "Fuel rail at ${railKpa.toInt()} kPa"
                Confidence.MEDIUM
            }
            else -> Confidence.LOW
        }

        if (code == "P1229") {
            signals += "SCV circuit fault alongside low rail pressure — high-pressure pump becomes the secondary suspect once the filter is ruled out"
        }

        val cause = if (confidence == Confidence.HIGH)
            "clogged fuel filter — cheap and the correct first hypothesis before checking the pump"
        else
            "fuel delivery fault — filter or SCV on the high-pressure pump"

        return Classification(
            primaryDtcCode = code,
            severity = Severity.warning,
            route = Route.diy,
            confidence = confidence,
            likelyCause = cause,
            supportingSignals = signals,
        )
    }

    private fun classifyLean(readings: Map<String, LiveReading>): Classification {
        val ltft = readings["Long-term fuel trim"]?.value?.toDoubleOrNull()
        val maf  = readings["MAF sensor"]?.value?.toDoubleOrNull()
        val signals = mutableListOf<String>()

        val confidence: Confidence
        val cause: String

        when {
            ltft != null && ltft >= 15 && maf != null && maf < 2.0 -> {
                signals += "Long-term fuel trim at +${ltft.toInt()}% — ECU has been adding extra fuel for an extended period to compensate for lean intake"
                signals += "MAF reading ${maf} g/s — below the normal idle range, pointing to a dirty or failing sensor"
                confidence = Confidence.HIGH
                cause = "dirty MAF sensor or clogged air filter — the two cheapest things to rule out first"
            }
            ltft != null && ltft >= 15 -> {
                signals += "Long-term fuel trim at +${ltft.toInt()}% — chronic lean condition, ECU continuously adding fuel"
                confidence = Confidence.MEDIUM
                cause = "lean air/fuel mix — dirty MAF sensor, clogged air filter, or a vacuum leak"
            }
            ltft != null && ltft >= 10 -> {
                signals += "Long-term fuel trim at +${ltft.toInt()}% — mild lean correction in progress"
                confidence = Confidence.MEDIUM
                cause = "mild lean condition — start with the air filter and MAF sensor"
            }
            else -> {
                confidence = Confidence.LOW
                cause = "lean air/fuel mix — cause unclear from available readings"
            }
        }

        if (maf != null && maf < 2.0 && confidence != Confidence.HIGH) {
            signals += "MAF reading ${maf} g/s — low for idle, consistent with a dirty or failing sensor"
        }

        return Classification(
            primaryDtcCode = "P0171",
            severity = Severity.warning,
            route = Route.diy,
            confidence = confidence,
            likelyCause = cause,
            supportingSignals = signals,
        )
    }

    private fun classifyMisfire(code: String, readings: Map<String, LiveReading>): Classification {
        val o2  = readings["O₂ sensor (bank 1)"]?.value?.toDoubleOrNull()
        val rpm = readings["RPM"]?.value?.toDoubleOrNull()
        val cylinder = code.last()
        val signals = mutableListOf<String>()

        val confidence: Confidence
        val cause: String

        when {
            o2 != null && o2 > 0.85 -> {
                signals += "O₂ sensor at ${o2}V — rich exhaust confirms unburned fuel escaping cylinder $cylinder (leaking injector or flooded plug)"
                confidence = Confidence.HIGH
                cause = "leaking injector on cylinder $cylinder — rich O₂ reading is the tell"
            }
            o2 != null && o2 < 0.2 -> {
                signals += "O₂ sensor at ${o2}V — lean exhaust means cylinder $cylinder isn't firing; unburned air is passing straight through"
                if (rpm != null && rpm < 750) {
                    signals += "RPM at ${rpm.toInt()} — rough idle confirms the cylinder is actively out of play"
                }
                confidence = Confidence.HIGH
                cause = "failed ignition coil on cylinder $cylinder — lean O₂ and rough idle are the two classic signs"
            }
            rpm != null && rpm < 750 -> {
                signals += "RPM at ${rpm.toInt()} — rough idle consistent with cylinder $cylinder not contributing to engine output"
                confidence = Confidence.MEDIUM
                cause = "cylinder $cylinder misfire causing rough idle — most likely a coil or spark plug"
            }
            else -> {
                confidence = Confidence.MEDIUM
                cause = "cylinder $cylinder misfire — ignition coil, spark plug, or injector"
            }
        }

        return Classification(
            primaryDtcCode = code,
            severity = Severity.warning,
            route = Route.diy,
            confidence = confidence,
            likelyCause = cause,
            supportingSignals = signals,
        )
    }

    private fun classifyRandomMisfire(readings: Map<String, LiveReading>): Classification {
        val ltft = readings["Long-term fuel trim"]?.value?.toDoubleOrNull()
        val maf  = readings["MAF sensor"]?.value?.toDoubleOrNull()
        val rpm  = readings["RPM"]?.value?.toDoubleOrNull()
        val signals = mutableListOf<String>()

        val confidence: Confidence
        val cause: String

        when {
            ltft != null && ltft >= 15 && maf != null && maf < 2.0 -> {
                signals += "Long-term fuel trim at +${ltft.toInt()}% — the ECU has been adding extra fuel across all cylinders, which points to the fuel system rather than individual coils"
                signals += "MAF reading ${maf} g/s — low, consistent with fuel starvation starving multiple cylinders at once"
                if (rpm != null && rpm < 750) signals += "RPM at ${rpm.toInt()} — rough idle confirms multiple cylinders are affected"
                confidence = Confidence.HIGH
                cause = "fuel starvation — low MAF and high fuel trim together rule out individual coil failures"
            }
            ltft != null && ltft >= 10 -> {
                signals += "Long-term fuel trim at +${ltft.toInt()}% — lean condition affecting multiple cylinders"
                confidence = Confidence.MEDIUM
                cause = "lean fuel delivery — dirty MAF or clogged air filter is the first thing to check"
            }
            else -> {
                if (rpm != null && rpm < 750) signals += "RPM at ${rpm.toInt()} — rough idle from multiple cylinders misfiring"
                confidence = Confidence.MEDIUM
                cause = "multiple cylinder misfire — needs diagnosis to separate fuel, ignition, or compression issues"
            }
        }

        return Classification(
            primaryDtcCode = "P0300",
            severity = Severity.severe,
            route = Route.expert,
            confidence = confidence,
            likelyCause = cause,
            supportingSignals = signals,
        )
    }

    private fun classifyIdleHigh(readings: Map<String, LiveReading>): Classification {
        val rpm  = readings["RPM"]?.value?.toDoubleOrNull()
        val stft = readings["Short-term fuel trim"]?.value?.toDoubleOrNull()
        val signals = mutableListOf<String>()

        val confidence: Confidence
        val cause: String

        when {
            rpm != null && rpm > 1000 && stft != null && stft < -3 -> {
                signals += "RPM at ${rpm.toInt()} — well above the normal 750-800 idle range"
                signals += "Short-term fuel trim at ${stft.toInt()}% — ECU cutting fuel to compensate for extra unmetered air entering through a leak"
                confidence = Confidence.HIGH
                cause = "vacuum leak — unmetered air is bypassing the MAF sensor and raising idle speed"
            }
            rpm != null && rpm > 1000 -> {
                signals += "RPM at ${rpm.toInt()} — idle is running too fast"
                confidence = Confidence.MEDIUM
                cause = "idle control fault or vacuum leak — check the intake hose and idle air control valve"
            }
            else -> {
                confidence = Confidence.LOW
                cause = "idle speed too high — cause unclear from available readings"
            }
        }

        return Classification(
            primaryDtcCode = "P0507",
            severity = Severity.warning,
            route = Route.diy,
            confidence = confidence,
            likelyCause = cause,
            supportingSignals = signals,
        )
    }

    private fun classifyGlowPlug(code: String, readings: Map<String, LiveReading>): Classification {
        val coolant = readings["Coolant temperature"]?.value?.toDoubleOrNull()
        val battery = readings["Battery voltage"]?.value?.toDoubleOrNull()
        val signals = mutableListOf<String>()

        val confidence: Confidence
        val cause: String

        when {
            coolant != null && coolant < 50 -> {
                signals += "Coolant at ${coolant.toInt()}°C — cold start conditions where glow plugs are critical for ignition"
                if (battery != null && battery < 12.3) {
                    signals += "Battery at ${battery}V — lower than normal, consistent with the engine working hard to start without proper pre-heating"
                }
                confidence = Confidence.HIGH
                cause = "failed glow plug — the cylinder isn't pre-heating before injection, making cold starts difficult or impossible"
            }
            else -> {
                if (coolant != null) signals += "Coolant at ${coolant.toInt()}°C"
                confidence = Confidence.MEDIUM
                cause = "glow plug circuit fault — cold starting will be unreliable, especially in cool weather"
            }
        }

        return Classification(
            primaryDtcCode = code,
            severity = Severity.warning,
            route = Route.diy,
            confidence = confidence,
            likelyCause = cause,
            supportingSignals = signals,
        )
    }

    private fun noSignal(code: String = "unknown") = Classification(
        primaryDtcCode = code,
        severity = Severity.warning,
        route = Route.expert,
        confidence = Confidence.LOW,
        likelyCause = "cause unclear — not enough readings to determine what triggered this code",
        supportingSignals = emptyList(),
    )
}
