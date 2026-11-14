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
    5, "Glowy", "Anglerfish, clownfish, betta, puffer or koi. Follows your finger.",
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

val NUTTY_INFO = CreatureInfo(
    7, "Nutty", "Twitchy red squirrel. Always one second from panicking.",
    "Tap: it freezes and puffs up. Drag: it hops after you. Hold: offer an acorn and watch it run off to bury it.",
    0xFFF2AC6A.toInt(), NUTTY_COATS.map { CoatInfo(it.name, it.fur, it.light) },
    listOf(
        EnvInfo("Autumn Oak", 18, c1 = 0xFFE8A33A.toInt(), c2 = 0xFF6E4A30.toInt()),
        EnvInfo("City Park", 19, c1 = 0xFF7EC8FF.toInt(), c2 = 0xFF5FB04F.toInt()),
        EnvInfo("Snowy Pines", 20, c1 = 0xFFDDEBFF.toInt(), c2 = 0xFF2E6B5B.toInt()),
    ),
    listOf(
        TrickInfo("nibble", "Nibble", "🌰"), TrickInfo("stash", "Bury a nut", "🕳️"), TrickInfo("lookout", "Lookout", "👀"),
        TrickInfo("flick", "Tail flick", "🐿️"), TrickInfo("zoom", "Zoomies", "💨"), TrickInfo("catch", "Catch!", "🫴"),
        TrickInfo("spin", "Spin", "🌀"), TrickInfo("scratch", "Scratch", "🦶"), TrickInfo("feed", "Give an acorn", "🎁"),
        TrickInfo("drum", "Drum solo", "🥁"),
    ),
)

val BOLT_INFO = CreatureInfo(
    8, "Bolt", "Pocket robot. Runs on your battery. TV face shows how it feels.",
    "Tap its face: new expression (tap fast and it overheats). Hold near it: it scans your finger.",
    0xFF2DD4BF.toInt(), BOLT_COATS.map { CoatInfo(it.name, it.body, it.screen) },
    listOf(
        EnvInfo("Workshop", 21, c1 = 0xFF2DD4BF.toInt(), c2 = 0xFFFFB36B.toInt()),
        EnvInfo("Arcade", 22, c1 = 0xFF9D4EDD.toInt(), c2 = 0xFF2DD4BF.toInt()),
        EnvInfo("Moon Base", 23, c1 = 0xFF8A93AE.toInt(), c2 = 0xFF59B8FF.toInt()),
    ),
    listOf(
        TrickInfo("clock", "What time?", "⏰"), TrickInfo("wave", "Wave", "👋"), TrickInfo("dance", "The robot", "🤖"),
        TrickInfo("polish", "Polish screen", "✨"), TrickInfo("glitch", "Glitch", "📺"), TrickInfo("tune", "Tune antenna", "📡"),
        TrickInfo("radar", "Radar", "🛰️"), TrickInfo("jump", "Jump", "⬆️"), TrickInfo("scan", "Scan", "🔍"),
        TrickInfo("faces", "Faces", "😎"),
    ),
)

val GLOOP_INFO = CreatureInfo(
    9, "Gloop Gang", "A crew of jelly blobs with their own games. Pick how many.",
    "Tap one: squish. Drag one and fling it into the others. Hold the screen: they all come over.",
    0xFF8EE03B.toInt(),
    GLOOP_SIZES.mapIndexed { i, n -> CoatInfo(if (n == 8) "Full gang" else "$n blobs", GLOOP_TRAITS[i * 2 % GLOOP_TRAITS.size].base, GLOOP_TRAITS[(i * 2 + 1) % GLOOP_TRAITS.size].base) },
    listOf(
        EnvInfo("Jelly Room", 28, c1 = 0xFFC9A6FF.toInt(), c2 = 0xFFFFD6F2.toInt()),
        EnvInfo("Gum Pool", 29, c1 = 0xFF73F2C8.toInt(), c2 = 0xFFFFC6DA.toInt()),
        EnvInfo("Moon Jelly", 30, c1 = 0xFF9D5CF2.toInt(), c2 = 0xFFFFE6FA.toInt()),
        EnvInfo("Sherbet Hills", 31, c1 = 0xFFFFB38F.toInt(), c2 = 0xFFFF9EB8.toInt()),
    ),
    listOf(
        TrickInfo("tag", "Tag", "🏃"), TrickInfo("tower", "Jelly tower", "🗼"), TrickInfo("conga", "Conga line", "💃"),
        TrickInfo("peekaboo", "Peekaboo", "🙈"), TrickInfo("bounce", "Bounce", "🔴"), TrickInfo("wave", "Mexican wave", "🌊"),
        TrickInfo("squish", "Squish all", "🫠"),
    ),
)

val CREATURES: List<CreatureInfo> by lazy { BASE_CREATURES + KIWI_INFO + GLOWY_INFO + GROMBLE_INFO + NUTTY_INFO + BOLT_INFO + GLOOP_INFO }

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
        TrickInfo("kiss", "Kiss the screen", "💋"),
        TrickInfo("talk", "Say something", "💬"), TrickInfo("whistle", "Whistle", "🎵"), TrickInfo("dance", "Headbang", "🤘"),
        TrickInfo("fly", "Fly a lap", "🪽"), TrickInfo("hang", "Upside-down", "🙃"), TrickInfo("seed", "Give a seed", "🌻"),
        TrickInfo("squawk", "Squawk!", "📢"), TrickInfo("preen", "Preen", "✨"), TrickInfo("oneleg", "One leg", "🦩"),
        TrickInfo("stretch", "Wing stretch", "🙆"),
    ),
)

fun creatureInfo(id: Int): CreatureInfo? = CREATURES.firstOrNull { it.id == id }
