#!/usr/bin/env bash
# Runs the on-device performance checks (needs one physical phone/tablet on adb, screen unlocked).
#
#   1. Macrobenchmarks: startup + 30 s frame timing with the 60 Hz mock emitter.
#   2. Baseline Profile generation (writes app/src/release/generated/baselineProfiles/).
#
# Results: benchmark/macro/build/outputs/connected_android_test_additional_output/…/*-benchmarkData.json
# plus the console summary (frameDurationCpuMs P50/P90/P95/P99, frameOverrunMs, startup times).
set -euo pipefail
cd "$(dirname "$0")/.."

adb get-state >/dev/null || { echo "No device on adb"; exit 1; }

./gradlew :benchmark:macro:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark "$@"

./gradlew :app:generateBaselineProfile

echo
echo "Frame timing acceptance: frameOverrunMs P99 <= 0 (no janky frames) and frameDurationCpuMs P99 < 16."
find benchmark/macro/build/outputs -name '*benchmarkData.json' -print
