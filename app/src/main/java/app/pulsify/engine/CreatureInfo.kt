package app.pulsify.engine

/** What the app screens need to know about each creature (id 1, 2, ...). */
data class CoatInfo(val name: String, val base: Int, val rim: Int)

/** An environment: a painted world id, or -1 for the night-sky shader with a given variant. */
data class EnvInfo(val name: String, val world: Int, val skyVariant: Int = 0, val c1: Int, val c2: Int)

/** A trick the Studio can ask the creature to do right now. */
data class TrickInfo(val id: String, val label: String, val emoji: String)

data class CreatureInfo(
    val id: Int,
    val name: String,
    val line: String,
    val hint: String,
    val accent: Int,
    val coats: List<CoatInfo>,
    val envs: List<EnvInfo>,
    val tricks: List<TrickInfo> = emptyList(),
)

val GLOWY_INFO = CreatureInfo(
    5, "Glowy", "An anglerfish whose lure follows your finger.",
    "Drag: the lure follows you. Touch the water: little fish gather and it gulps them. Tap: the lure flashes.",
    0xFF80F2FF.toInt(), GLOWY_COATS.map { CoatInfo(it.name, it.body, it.glow) },
    listOf(
        EnvInfo("Midnight Trench", 12, c1 = 0xFF0A1F4D.toInt(), c2 = 0xFF4DE6FF.toInt()),
        EnvInfo("Coral Garden", 13, c1 = 0xFF1FA6C7.toInt(), c2 = 0xFFFF8099.toInt()),
        EnvInfo("Sunken Ship", 14, c1 = 0xFF14666B.toInt(), c2 = 0xFFFFD159.toInt()),
    ),
    listOf(
        TrickInfo("feed", "Feed", "🐟"), TrickInfo("flash", "Flash the lure", "💡"), TrickInfo("puff", "Puff up", "🐡"),
        TrickInfo("loop", "Loop", "🌀"), TrickInfo("bubbles", "Bubbles", "🫧"), TrickInfo("yawn", "Big yawn", "🥱"),
        TrickInfo("stare", "Chase the lure", "👀"),
    ),
)

val GROMBLE_INFO = CreatureInfo(
    6, "Gromble", "Tries very hard to be evil. Fails adorably.",
    "Finger far away: he growls. Finger close: he panics. Poke him three times. Hold him: secretly loves it.",
    0xFFB380FF.toInt(), GROMBLE_COATS.map { CoatInfo(it.name, it.fur, it.horn) },
    listOf(
        EnvInfo("Lava Lair", 15, c1 = 0xFF4D0D0D.toInt(), c2 = 0xFFFF8C1A.toInt()),
        EnvInfo("Haunted Hill", 16, c1 = 0xFF3D2E6B.toInt(), c2 = 0xFFFFBF4D.toInt()),
        EnvInfo("Evil HQ", 17, c1 = 0xFF5C3D8C.toInt(), c2 = 0xFF99FF59.toInt()),
    ),
    listOf(
        TrickInfo("laugh", "Evil laugh", "😈"), TrickInfo("plan", "Evil plan", "📜"), TrickInfo("fire", "Fireball", "🔥"),
        TrickInfo("fly", "Fly", "🦇"), TrickInfo("boo", "BOO!", "👻"), TrickInfo("cape", "Cape swish", "🧛"),
        TrickInfo("attack", "Attack!", "💥"), TrickInfo("pet", "Pet him", "🫶"),
    ),
)

val CREATURES: List<CreatureInfo> by lazy { BASE_CREATURES + KIWI_INFO + GLOWY_INFO + GROMBLE_INFO }

private val BASE_CREATURES = listOf(
    CreatureInfo(
        1, "Mochi", "Battery-juice bean. Don't take its jar.",
        "Tap Mochi. Hold it. Try stealing its jar while you charge.",
        0xFF7CF2A8.toInt(), MOCHI_COATS.map { CoatInfo(it.name, it.base, it.rim) },
        listOf(
            EnvInfo("Candy Cosmos", -1, 0, 0xFF5BF09A.toInt(), 0xFFC9A6FF.toInt()),
            EnvInfo("Cloud Nursery", 1, c1 = 0xFFF5A8D8.toInt(), c2 = 0xFF8C7AE6.toInt()),
            EnvInfo("Battery Lab", 2, c1 = 0xFF4ADE80.toInt(), c2 = 0xFF1B2430.toInt()),
        ),
        listOf(TrickInfo("hello", "Say hi", "👋"), TrickInfo("bubble", "Blow a bubble", "🫧"), TrickInfo("star", "Catch a star", "⭐"), TrickInfo("bird", "Bird visit", "🐦"), TrickInfo("party", "Party", "🎉"), TrickInfo("wobble", "Wobble", "🍮"), TrickInfo("giggle", "Giggle", "😆"), TrickInfo("stretch", "Big stretch", "🙆")),
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
        listOf(TrickInfo("fetch", "Fetch", "🎾"), TrickInfo("paw", "Give paw", "🐾"), TrickInfo("belly", "Belly rub", "🫶"), TrickInfo("dig", "Dig", "🕳️"), TrickInfo("howl", "Howl", "🌕"), TrickInfo("butterfly", "Butterfly", "🦋"), TrickInfo("zoomies", "Zoomies", "💨"), TrickInfo("yawn", "Yawn", "🥱")),
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
        listOf(TrickInfo("pounce", "Pounce", "🎯"), TrickInfo("knock", "Knock the cup", "☕"), TrickInfo("cucumber", "Cucumber!", "🥒"), TrickInfo("box", "If it fits", "📦"), TrickInfo("zoomies", "Zoomies", "💨"), TrickInfo("purr", "Purr", "💜"), TrickInfo("groom", "Groom", "👅"), TrickInfo("loaf", "Loaf", "🍞"), TrickInfo("trill", "Trill", "🎶"), TrickInfo("stretch", "Stretch", "🙆")),
    ),
)

val KIWI_INFO = CreatureInfo(
    4, "Kiwi", "Talks, whistles, headbangs. Teach it words.",
    "Drag: it steps to your finger. Tap it: squawk. Hold the sky: a seed. Tap a rhythm: it copies you.",
    0xFF7BE08A.toInt(), KIWI_SPECIES.map { CoatInfo(it.name, it.body, it.face) },
    listOf(
        EnvInfo("Rainforest", 9, c1 = 0xFF2E7D4F.toInt(), c2 = 0xFFB8F27A.toInt()),
        EnvInfo("Palm Beach", 10, c1 = 0xFF3FC1FF.toInt(), c2 = 0xFFFFC46B.toInt()),
        EnvInfo("Blossom Garden", 11, c1 = 0xFFFFB7D5.toInt(), c2 = 0xFF8C6CE6.toInt()),
    ),
    listOf(
        TrickInfo("talk", "Say something", "💬"), TrickInfo("whistle", "Whistle", "🎵"), TrickInfo("dance", "Headbang", "🤘"),
        TrickInfo("fly", "Fly a lap", "🪽"), TrickInfo("hang", "Upside-down", "🙃"), TrickInfo("seed", "Give a seed", "🌻"),
        TrickInfo("squawk", "Squawk!", "📢"), TrickInfo("preen", "Preen", "✨"), TrickInfo("oneleg", "One leg", "🦩"),
        TrickInfo("stretch", "Wing stretch", "🙆"),
    ),
)

fun creatureInfo(id: Int): CreatureInfo? = CREATURES.firstOrNull { it.id == id }
