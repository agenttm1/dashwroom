package com.dashwroom.f1telemetry.replay.mock

/** Which kind of session the mock simulates. */
enum class MockSessionMode(val sessionType: Int, val label: String) {
    /** Session type 15. */
    RACE(15, "Race"),

    /** Session type 5 (Q1): cars run out lap → flying laps → in lap → garage, ranked by best lap. */
    QUALIFYING(5, "Qualifying (Q1)"),
}
