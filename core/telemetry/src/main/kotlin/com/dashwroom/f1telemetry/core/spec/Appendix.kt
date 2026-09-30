package com.dashwroom.f1telemetry.core.spec

/**
 * Lookup tables from the spec appendices. Source: "Data Output from F1 25: 2026 Season Pack"
 * v1.2 (Season 8), which is a superset of the F1 25 v3 tables. Unknown ids fall back to a
 * readable "Unknown (n)" string instead of throwing.
 */
object Appendix {

    fun trackName(id: Int): String = tracks[id] ?: if (id < 0) "Unknown track" else "Track $id"

    fun sessionTypeName(id: Int): String = sessionTypes[id] ?: "Session $id"

    fun teamName(id: Int): String = teams[id] ?: "Team $id"

    fun driverName(id: Int): String? = drivers[id]

    fun gameModeName(id: Int): String = gameModes[id] ?: "Mode $id"

    fun weatherName(id: Int): String = weather.getOrElse(id) { "Weather $id" }

    fun formulaName(id: Int): String = formulas[id] ?: "Formula $id"

    fun safetyCarStatusName(id: Int): String = safetyCarStatus.getOrElse(id) { "Unknown" }

    val tracks: Map<Int, String> = mapOf(
        0 to "Melbourne", 2 to "Shanghai", 3 to "Sakhir (Bahrain)", 4 to "Catalunya",
        5 to "Monaco", 6 to "Montreal", 7 to "Silverstone", 9 to "Hungaroring", 10 to "Spa",
        11 to "Monza", 12 to "Singapore", 13 to "Suzuka", 14 to "Abu Dhabi", 15 to "Texas",
        16 to "Brazil", 17 to "Austria", 19 to "Mexico", 20 to "Baku (Azerbaijan)",
        26 to "Zandvoort", 27 to "Imola", 29 to "Jeddah", 30 to "Miami", 31 to "Las Vegas",
        32 to "Losail", 39 to "Silverstone (Reverse)", 40 to "Austria (Reverse)",
        41 to "Zandvoort (Reverse)", 42 to "Madrid",
    )

    val sessionTypes: Map<Int, String> = mapOf(
        0 to "Unknown", 1 to "Practice 1", 2 to "Practice 2", 3 to "Practice 3",
        4 to "Short Practice", 5 to "Qualifying 1", 6 to "Qualifying 2", 7 to "Qualifying 3",
        8 to "Short Qualifying", 9 to "One-Shot Qualifying", 10 to "Sprint Shootout 1",
        11 to "Sprint Shootout 2", 12 to "Sprint Shootout 3", 13 to "Short Sprint Shootout",
        14 to "One-Shot Sprint Shootout", 15 to "Race", 16 to "Race 2", 17 to "Race 3",
        18 to "Time Trial",
    )

    val teams: Map<Int, String> = mapOf(
        0 to "Mercedes", 1 to "Ferrari", 2 to "Red Bull Racing", 3 to "Williams",
        4 to "Aston Martin", 5 to "Alpine", 6 to "RB", 7 to "Haas", 8 to "McLaren", 9 to "Sauber",
        41 to "F1 Generic", 104 to "F1 Custom Team", 129 to "Konnersport", 142 to "APXGP '24",
        154 to "APXGP '25", 155 to "Konnersport '24", 158 to "Art GP '24", 159 to "Campos '24",
        160 to "Rodin Motorsport '24", 161 to "AIX Racing '24", 162 to "DAMS '24",
        163 to "Hitech '24", 164 to "MP Motorsport '24", 165 to "Prema '24", 166 to "Trident '24",
        167 to "Van Amersfoort Racing '24", 168 to "Invicta '24", 185 to "Mercedes '24",
        186 to "Ferrari '24", 187 to "Red Bull Racing '24", 188 to "Williams '24",
        189 to "Aston Martin '24", 190 to "Alpine '24", 191 to "RB '24", 192 to "Haas '24",
        193 to "McLaren '24", 194 to "Sauber '24",
        // 2026 Season Pack additions (uint16 team ids).
        465 to "Art GP '25", 466 to "Campos '25", 467 to "Rodin Motorsport '25",
        468 to "AIX Racing '25", 469 to "DAMS '25", 470 to "Hitech '25", 471 to "MP Motorsport '25",
        472 to "Prema '25", 473 to "Trident '25", 474 to "Van Amersfoort Racing '25",
        475 to "Invicta '25", 476 to "Mercedes '26", 477 to "Ferrari '26",
        478 to "Red Bull Racing '26", 479 to "Williams '26", 480 to "Aston Martin '26",
        481 to "Alpine '26", 482 to "RB '26", 483 to "Haas '26", 484 to "McLaren '26",
        485 to "Audi '26", 486 to "Cadillac '26", 489 to "Art GP '26", 490 to "Campos '26",
        491 to "Rodin Motorsport '26", 492 to "AIX Racing '26", 493 to "DAMS '26",
        494 to "Hitech '26", 495 to "MP Motorsport '26", 496 to "Prema '26", 497 to "Trident '26",
        498 to "Van Amersfoort Racing '26", 499 to "Invicta '26",
    )

    val drivers: Map<Int, String> = mapOf(
        0 to "Carlos Sainz", 2 to "Daniel Ricciardo", 3 to "Fernando Alonso", 4 to "Felipe Massa",
        7 to "Lewis Hamilton", 9 to "Max Verstappen", 10 to "Nico Hülkenberg",
        11 to "Kevin Magnussen", 14 to "Sergio Pérez", 15 to "Valtteri Bottas",
        17 to "Esteban Ocon", 19 to "Lance Stroll", 20 to "Arron Barnes", 21 to "Martin Giles",
        22 to "Alex Murray", 23 to "Lucas Roth", 24 to "Igor Correia", 25 to "Sophie Levasseur",
        26 to "Jonas Schiffer", 27 to "Alain Forest", 28 to "Jay Letourneau", 29 to "Esto Saari",
        30 to "Yasar Atiyeh", 31 to "Callisto Calabresi", 32 to "Naota Izumi",
        33 to "Howard Clarke", 34 to "Lars Kaufmann", 35 to "Marie Laursen",
        36 to "Flavio Nieves", 38 to "Klimek Michalski", 39 to "Santiago Moreno",
        40 to "Benjamin Coppens", 41 to "Noah Visser", 50 to "George Russell",
        54 to "Lando Norris", 58 to "Charles Leclerc", 59 to "Pierre Gasly",
        62 to "Alexander Albon", 70 to "Rashid Nair", 71 to "Jack Tremblay", 77 to "Ayrton Senna",
        80 to "Guanyu Zhou", 83 to "Juan Manuel Correa", 90 to "Michael Schumacher",
        94 to "Yuki Tsunoda", 102 to "Aidan Jackson", 109 to "Jenson Button",
        110 to "David Coulthard", 112 to "Oscar Piastri", 113 to "Liam Lawson",
        116 to "Richard Verschoor", 123 to "Enzo Fittipaldi", 125 to "Mark Webber",
        126 to "Jacques Villeneuve", 127 to "Callie Mayer", 132 to "Logan Sargeant",
        136 to "Jack Doohan", 137 to "Amaury Cordeel", 138 to "Dennis Hauger",
        145 to "Zane Maloney", 146 to "Victor Martins", 147 to "Oliver Bearman",
        148 to "Jak Crawford", 149 to "Isack Hadjar", 152 to "Roman Stanek", 153 to "Kush Maini",
        156 to "Brendon Leigh", 157 to "David Tonizza", 158 to "Jarno Opmeer",
        159 to "Lucas Blakeley", 160 to "Paul Aron", 161 to "Gabriel Bortoleto",
        162 to "Franco Colapinto", 163 to "Taylor Barnard", 164 to "Joshua Dürksen",
        165 to "Andrea-Kimi Antonelli", 166 to "Ritomo Miyata", 167 to "Rafael Villagómez",
        168 to "Zak O'Sullivan", 169 to "Pepe Marti", 170 to "Sonny Hayes", 171 to "Joshua Pearce",
        172 to "Callum Voisin", 173 to "Matias Zagazeta", 174 to "Nikola Tsolov",
        175 to "Tim Tramnitz", 185 to "Luca Cortez", 186 to "Luke Browning", 187 to "Cian Shields",
        188 to "Arvid Lindblad", 189 to "Dino Beganovic", 190 to "Leonardo Fornaroli",
        191 to "Oliver Goethe", 192 to "Gabriele Minì", 193 to "Sebastián Montoya",
        194 to "Alexander Dunne", 195 to "Max Esterson", 196 to "Sami Meguetounif",
        197 to "John Bennett", 198 to "Emerson Fittipaldi", 199 to "Tasanopol Inthraphuvasak",
        200 to "Noel León", 201 to "Roman Bilinski", 202 to "Colton Herta", 203 to "Rafael Câmara",
        204 to "Mari Boya", 205 to "Martinius Stenshorne", 206 to "Laurens van Hoepen",
        207 to "Nico Varrone",
    )

    val gameModes: Map<Int, String> = mapOf(
        4 to "Grand Prix '23", 5 to "Time Trial", 6 to "Splitscreen", 7 to "Online Custom",
        15 to "Online Weekly Event", 17 to "Story Mode (Braking Point)", 27 to "My Team Career '25",
        28 to "Driver Career '25", 29 to "Career '25 Online", 30 to "Challenge Career '25",
        75 to "Story Mode (APXGP)", 127 to "Benchmark",
    )

    val formulas: Map<Int, String> = mapOf(
        0 to "F1 Modern", 1 to "F1 Classic", 2 to "F2", 3 to "F1 Generic", 4 to "Beta",
        6 to "Esports", 8 to "F1 World", 9 to "F1 Elimination", 13 to "F1 26",
    )

    private val weather = listOf("Clear", "Light cloud", "Overcast", "Light rain", "Heavy rain", "Storm")

    private val safetyCarStatus = listOf("None", "Safety Car", "Virtual Safety Car", "Formation Lap")

    /** Ruleset ids: 0 practice & quali, 1 race, 2 time trial, 12 elimination. */
    val ruleSets: Map<Int, String> = mapOf(0 to "Practice & Qualifying", 1 to "Race", 2 to "Time Trial", 12 to "Elimination")

    val surfaceTypes = listOf(
        "Tarmac", "Rumble strip", "Concrete", "Rock", "Gravel", "Mud", "Sand", "Grass", "Water",
        "Cobblestone", "Metal", "Ridged",
    )

    val penaltyTypes = listOf(
        "Drive through", "Stop Go", "Grid penalty", "Penalty reminder", "Time penalty", "Warning",
        "Disqualified", "Removed from formation lap", "Parked too long timer", "Tyre regulations",
        "This lap invalidated", "This and next lap invalidated", "This lap invalidated without reason",
        "This and next lap invalidated without reason", "This and previous lap invalidated",
        "This and previous lap invalidated without reason", "Retired", "Black flag timer",
    )

    val infringementTypes = listOf(
        "Blocking by slow driving", "Blocking by wrong way driving", "Reversing off the start line",
        "Big Collision", "Small Collision", "Collision failed to hand back position single",
        "Collision failed to hand back position multiple", "Corner cutting gained time",
        "Corner cutting overtake single", "Corner cutting overtake multiple", "Crossed pit exit lane",
        "Ignoring blue flags", "Ignoring yellow flags", "Ignoring drive through",
        "Too many drive throughs", "Drive through reminder serve within n laps",
        "Drive through reminder serve this lap", "Pit lane speeding", "Parked for too long",
        "Ignoring tyre regulations", "Too many penalties", "Multiple warnings",
        "Approaching disqualification", "Tyre regulations select single",
        "Tyre regulations select multiple", "Lap invalidated corner cutting",
        "Lap invalidated running wide", "Corner cutting ran wide gained time minor",
        "Corner cutting ran wide gained time significant", "Corner cutting ran wide gained time extreme",
        "Lap invalidated wall riding", "Lap invalidated flashback used", "Lap invalidated reset to track",
        "Blocking the pitlane", "Jump start", "Safety car to car collision",
        "Safety car illegal overtake", "Safety car exceeding allowed pace",
        "Virtual safety car exceeding allowed pace", "Formation lap below allowed speed",
        "Formation lap parking", "Retired mechanical failure", "Retired terminally damaged",
        "Safety car falling too far back", "Black flag timer", "Unserved stop go penalty",
        "Unserved drive through penalty", "Engine component change", "Gearbox change",
        "Parc Fermé change", "League grid penalty", "Retry penalty", "Illegal time gain",
        "Mandatory pitstop", "Attribute assigned",
    )
}
