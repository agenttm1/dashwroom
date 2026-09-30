# Dashwroom — F1 25 live telemetry dashboard (Android)

The phone listens directly for the UDP telemetry EA SPORTS F1® 25 broadcasts on the local
network — no PC bridge, no server. Kotlin, 100 % Jetpack Compose, Material 3, dark-first.

> **Status: Phase 1 (foundation).** Receiving, parsing, diagnostics, mock/replay sources and the
> adaptive shell are done; Overview/Race/Qualifying/Car/Laps are placeholders until Phases 2–4.

## Quick start

1. Install the app on a phone on the **same Wi-Fi** as your PC/PS5/Xbox (5 GHz recommended).
2. Open **Connect** (tap the status pill): it shows the phone's IP in large type.
3. In F1 25 › Settings › Telemetry Settings: UDP Telemetry **On**, Broadcast **Off**,
   IP **= phone IP**, Port **20777**, Send Rate **60 Hz**, Format **2025** (or **2026** with the
   2026 Season Pack).
4. No game handy? Settings › Data source › **Mock** runs a synthetic 20-lap race at 60 Hz.

## Modules

| Module | What | Android? |
|---|---|---|
| `:core:telemetry` | Spec-exact packet parsers (2025 + 2026 formats), encoder, ingest pipeline, `TelemetryRepository`, lock-free `HotTelemetry` | plain Kotlin/JVM |
| `:replay` | `MockTelemetryEmitter` (procedural circuit + race sim), `.bin` recorder/replayer | plain Kotlin/JVM |
| `:app` | Compose UI, Hilt, DataStore settings, foreground `UdpTelemetryService`, allocation-free `OsUdpSource` | Android |
| `:benchmark:jmh` | Parser/ingest microbenchmarks | JVM |
| `:benchmark:macro` | Macrobenchmark (startup, frame timing) + Baseline Profile generator | Android test |

The official EA UDP specifications this is built from are in [`docs/spec/`](docs/spec/); every
parser documents its byte offsets against them.

## Data flow

```
socket (drain burst) ─▶ IngestStats ─▶ [recorder tap] ─▶ coalescer (newest per type)
   ─▶ PacketParser (reused objects) ─▶ TelemetryStore ─┬▶ HotTelemetry (volatile fields, read per frame in draw)
                                                        └▶ StateFlow<SessionState> (immutable, on change)
TelemetryRepository.status: StateFlow<TelemetryStatus> (4 Hz diagnostics, SEARCHING/CONNECTED/STALE)
```

All of it runs on one dedicated `telemetry-ingest` thread and allocates nothing per packet.

## Build, test, benchmark

```bash
./gradlew assembleDebug                 # or assembleRelease (R8 + shrinking)
./gradlew test                          # 59 JVM tests: parsers, pipeline, mock, replay, Robolectric UI
./gradlew :app:recordRoborazziDebug     # re-render app/screenshots/*.png
./gradlew :benchmark:jmh:jmh            # parser microbenchmarks (see docs/benchmarks/PHASE1.md)
scripts/run-device-benchmarks.sh        # macrobenchmarks + baseline profile (needs a device)
```

Requires JDK 17+ and Android SDK platform 37 (compileSdk 37; targetSdk 36; minSdk 26).
