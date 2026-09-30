# Phase 1 performance results

## Parser microbenchmark (JMH) — measured

`./gradlew :benchmark:jmh:jmh` · JMH 1.37 · OpenJDK 21 HotSpot, 4 vCPU cloud container ·
`-XX:-DoEscapeAnalysis` (ART has no escape analysis, so HotSpot must not hide allocations) ·
packets captured from the mock race 45 s into a session. Raw data: [`jmh-phase1.json`](jmh-phase1.json).

| Benchmark | Format 2025 | Format 2026 | Allocated |
|---|---|---|---|
| `parseFrameSet` — one 60 Hz set: Motion + Lap Data + Car Telemetry + Car Status | **0.82 µs** ± 0.03 | **1.02 µs** ± 0.03 | ≈ 0 B/op |
| `ingestFrameSet` — full ingest thread work: stats + loss tracking + coalescing + parse + state update | **1.43 µs** ± 0.07 | **1.57 µs** ± 0.05 | ≈ 0 B/op |
| `parseSessionAndParticipants` — the two largest low-rate packets | 0.60 µs | 0.63 µs | ≈ 0 B/op |

Criterion "well under 1 ms and allocates nothing": **met** — ~700× under budget on HotSpot;
even a 10–20× slower phone core leaves >30× headroom. "≈ 0 B/op" is JMH's own noise floor
(0.001 B/op); `ZeroAllocationTest` asserts exactly 0 bytes over 10 000 frames per format.

## On-device checks — not yet run

This cloud container has no KVM, so no emulator or device. The following exist and compile but
need a physical device (`scripts/run-device-benchmarks.sh`):

- `FrameTimingBenchmark.connectScreenUnderMock60Hz` — FrameTimingMetric, 3 × 30 s with the
  60 Hz mock. Acceptance: 0 janky frames, P99 frame time < 16 ms. The Race-screen variant (the
  real target) is added in Phase 2 with the Race screen.
- `StartupBenchmark` — cold start, with and without the Baseline Profile.
- `BaselineProfileGenerator` — produces `app/src/release/generated/baselineProfiles/`.
- In-app: Settings › Debug › Performance HUD (fps, receive→draw latency, janky frames,
  allocation rate); JankStats logs janky frames to logcat (tag `Jank`) in debug builds.
