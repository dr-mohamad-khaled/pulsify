package app.pulsify.engine

/** What the app screens need to know about each creature (id 1, 2, ...). */
data class CoatInfo(val name: String, val base: Int, val rim: Int)

/** An environment: a painted world id, or -1 for the night-sky shader with a given variant. */
data class EnvInfo(val name: String, val world: Int, val skyVariant: Int = 0, val c1: Int, val c2: Int)

data class CreatureInfo(
    val id: Int,
    val name: String,
    val line: String,
    val hint: String,
    val accent: Int,
    val coats: List<CoatInfo>,
    val envs: List<EnvInfo>,
)

val CREATURES = listOf(
    CreatureInfo(
        1, "Mochi", "Battery-juice bean. Don't take its jar.",
        "Tap Mochi. Hold it. Try stealing its jar while you charge.",
        0xFF7CF2A8.toInt(), MOCHI_COATS.map { CoatInfo(it.name, it.base, it.rim) },
        listOf(
            EnvInfo("Candy Cosmos", -1, 0, 0xFF5BF09A.toInt(), 0xFFC9A6FF.toInt()),
            EnvInfo("Cloud Nursery", 1, c1 = 0xFFF5A8D8.toInt(), c2 = 0xFF8C7AE6.toInt()),
            EnvInfo("Battery Lab", 2, c1 = 0xFF4ADE80.toInt(), c2 = 0xFF1B2430.toInt()),
        ),
    ),
    CreatureInfo(
        2, "Kalboob", "Golden pup. Will fetch anything.",
        "Pet Kalboob. Hold for belly rubs. Hold the ground to throw a ball.",
        0xFFFFC46B.toInt(), PUP_COATS.map { CoatInfo(it.name, it.fur, it.collar) },
        listOf(
            EnvInfo("Backyard", 3, c1 = 0xFF5FB2FF.toInt(), c2 = 0xFF4CAF50.toInt()),
            EnvInfo("Snow Day", 4, c1 = 0xFFDDEBFF.toInt(), c2 = 0xFF2E7D6B.toInt()),
            EnvInfo("Cozy Room", 5, c1 = 0xFFE0A070.toInt(), c2 = 0xFF8E3A50.toInt()),
        ),
    ),
    CreatureInfo(
        3, "Kitty", "Curious, aloof, a little dramatic.",
        "Drag a finger: laser dot. Hold Kitty to make it purr.",
        0xFF8FC7FF.toInt(), KITTY_COATS.map { CoatInfo(it.name, it.fur, it.eye) },
        listOf(
            EnvInfo("Windowsill", 6, c1 = 0xFF8FC7FF.toInt(), c2 = 0xFFE9EEF5.toInt()),
            EnvInfo("Rooftop", 7, c1 = 0xFF1F2A44.toInt(), c2 = 0xFFFFC56B.toInt()),
            EnvInfo("Box Fort", 8, c1 = 0xFFD9A066.toInt(), c2 = 0xFFF06292.toInt()),
        ),
    ),
)

fun creatureInfo(id: Int): CreatureInfo? = CREATURES.firstOrNull { it.id == id }
