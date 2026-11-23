package app.pulsify.engine

/** What the app screens need to know about each creature (id 1, 2, ...). */
data class CoatInfo(val name: String, val base: Int, val rim: Int)

/** An environment: a painted world id, or -1 for the night-sky shader with a given variant. */
data class EnvInfo(val name: String, val world: Int, val skyVariant: Int = 0, val c1: Int, val c2: Int)

/** World id for a plain background in a colour the user picks (true black by default). */
const val SOLID_WORLD = 100

/** World id for the user's own photo from the phone (one per creature, with the creature placed where they chose). */
const val USER_WORLD = 101
/** Glowy's Aquarium look: its tanks are worlds AQUA_WORLD0 + tank index (the aquarium paints them itself). */
const val AQUA_WORLD0 = 200
/** Gromble (Reaper, Mischief): the 3D worlds drawn in code that turn with the camera, ORBIT_WORLD0 + index. */
const val ORBIT_WORLD0 = 300

/** Worlds the shared world painter draws (pictures, the old procedural paintings, the user's photo). */
fun paintedWorld(world: Int): Boolean = (world in 0 until SOLID_WORLD) || world == USER_WORLD

val PLAIN_ENV = EnvInfo("Plain colour", SOLID_WORLD, c1 = 0xFF000000.toInt(), c2 = 0xFF111111.toInt())
val PHOTO_ENV = EnvInfo("Your photo", USER_WORLD, c1 = 0xFF2A2338.toInt(), c2 = 0xFF15121E.toInt())

/** Background colours offered for [SOLID_WORLD]. */
val SOLID_COLORS = listOf(
    0xFF000000.toInt(), 0xFF0B1026.toInt(), 0xFF1A0B24.toInt(), 0xFF06201C.toInt(),
    0xFF1F1F24.toInt(), 0xFF2A0A14.toInt(), 0xFF2B1E0A.toInt(), 0xFFF6EFE6.toInt(),
)

/** Photo worlds (four times of day, depth parallax, living layers). Only the ones whose pictures ship are offered. */
val P_BACKYARD = EnvInfo("Backyard", 40, c1 = 0xFF5FB2FF.toInt(), c2 = 0xFF4CAF50.toInt())
val P_SNOWCABIN = EnvInfo("Snowy Cabin", 41, c1 = 0xFFDDEBFF.toInt(), c2 = 0xFF2E6B5B.toInt())
val P_LIVING = EnvInfo("Living Room", 42, c1 = 0xFFE0A070.toInt(), c2 = 0xFF8E3A50.toInt())
val P_ROOFTOP = EnvInfo("Rooftop Garden", 43, c1 = 0xFF1F2A44.toInt(), c2 = 0xFFFFC56B.toInt())
val P_RAINFOREST = EnvInfo("Rainforest", 44, c1 = 0xFF2E7D4F.toInt(), c2 = 0xFFB8F27A.toInt())
val P_BEACH = EnvInfo("Tropical Beach", 45, c1 = 0xFF3FC1FF.toInt(), c2 = 0xFFFFC46B.toInt())
val P_POND = EnvInfo("Pond Meadow", 46, c1 = 0xFF7ED957.toInt(), c2 = 0xFF5FB2FF.toInt())
val P_BLOSSOM = EnvInfo("Blossom Garden", 47, c1 = 0xFFFFB7D5.toInt(), c2 = 0xFF8C6CE6.toInt())
val P_AUTUMN = EnvInfo("Autumn Forest", 48, c1 = 0xFFE8A33A.toInt(), c2 = 0xFF6E4A30.toInt())
val P_PARK = EnvInfo("City Park", 49, c1 = 0xFF7EC8FF.toInt(), c2 = 0xFF5FB04F.toInt())
val P_WORKSHOP = EnvInfo("Workshop", 50, c1 = 0xFF2DD4BF.toInt(), c2 = 0xFFFFB36B.toInt())
val P_MOONBASE = EnvInfo("Moon Base", 51, c1 = 0xFF8A93AE.toInt(), c2 = 0xFF59B8FF.toInt())
val P_GRAVEYARD = EnvInfo("Spooky Graveyard", 52, c1 = 0xFF3D2E6B.toInt(), c2 = 0xFFFFBF4D.toInt())
val P_PUMPKIN = EnvInfo("Pumpkin Patch", 53, c1 = 0xFFFF8C1A.toInt(), c2 = 0xFF6E4A30.toInt())
val P_REEF = EnvInfo("Coral Reef", 54, c1 = 0xFF1FA6C7.toInt(), c2 = 0xFFFF8099.toInt())
val P_SHIP = EnvInfo("Sunken Ship", 55, c1 = 0xFF14666B.toInt(), c2 = 0xFFFFD159.toInt())
val P_CANDY = EnvInfo("Candy Land", 56, c1 = 0xFFFFB3D9.toInt(), c2 = 0xFF8EF2D0.toInt())
val P_CLOUD = EnvInfo("Cloud Nursery", 57, c1 = 0xFFF5A8D8.toInt(), c2 = 0xFF8C7AE6.toInt())
val P_SAVANNA = EnvInfo("Savanna", 58, c1 = 0xFFE8B04A.toInt(), c2 = 0xFF6E8A3A.toInt())
val P_WATERHOLE = EnvInfo("Watering Hole", 59, c1 = 0xFF59B8FF.toInt(), c2 = 0xFFE8B04A.toInt())

// 5.9 high-quality worlds (ids 60+). Each shows up once its pictures are in assets/worlds (see PHOTO_READY).
val W_LIVING = EnvInfo("Cosy Living Room", 60, c1 = -2056080, c2 = -7456176)
val W_SNOWYARD = EnvInfo("Snowy Cabin", 61, c1 = -2233345, c2 = -13735077)
val W_GARDEN = EnvInfo("Family Garden", 62, c1 = -8464041, c2 = -1754827)
val W_SAVANNA = EnvInfo("Savanna", 63, c1 = -1527734, c2 = -9532870)
val W_WATERHOLE = EnvInfo("Waterhole", 64, c1 = -10897153, c2 = -1527734)
val W_FOREST = EnvInfo("Autumn Forest", 65, c1 = -1531078, c2 = -9549264)
val W_PARK = EnvInfo("City Park", 66, c1 = -8468225, c2 = -10506161)
val W_TERRACE = EnvInfo("Sea Terrace", 67, c1 = -12598785, c2 = -1754827)
val W_GREENHOUSE = EnvInfo("Greenhouse", 68, c1 = -13730481, c2 = -4656518)
val W_POND = EnvInfo("Lily Pond", 69, c1 = -8464041, c2 = -10505473)
val W_FARM = EnvInfo("Farmyard", 70, c1 = -1754827, c2 = -1522838)
val W_GRAVEYARD = EnvInfo("Moonlit Graveyard", 71, c1 = -12767637, c2 = -16563)
val W_PUMPKINS = EnvInfo("Pumpkin Patch", 72, c1 = -29670, c2 = -9549264)
val W_LAB = EnvInfo("Robot Lab", 73, c1 = -13773633, c2 = -2037761)
val W_MOONBASE = EnvInfo("Moon Base", 74, c1 = -7695442, c2 = -10897153)
val W_AQUARIUM = EnvInfo("Planted Tank", 75, c1 = -14702982, c2 = -1739917)
val W_BLACKWATER = EnvInfo("Blackwater Tank", 76, c1 = -7513557, c2 = -11633350)
val W_BALCONY = EnvInfo("Balcony Garden", 77, c1 = -5005861, c2 = -14997)

// 7.0 worlds (ids 80+): Flow paintings in four times of day, made for each creature (tools/bg/make_world.py).
val V_PLAYROOM = EnvInfo("Pastel Playroom", 80, c1 = 0xFFC9B6F2.toInt(), c2 = 0xFFFFD6E8.toInt())
val V_CANDY = EnvInfo("Candy Meadow", 81, c1 = 0xFFFFB3D9.toInt(), c2 = 0xFF8EF2D0.toInt())
val V_CLOUD = EnvInfo("Cloud Garden", 82, c1 = 0xFFF5D0E8.toInt(), c2 = 0xFF8CB8F2.toInt())
val V_CANOPY = EnvInfo("Canopy Deck", 83, c1 = 0xFF2E7D4F.toInt(), c2 = 0xFFB8F27A.toInt())
val V_LAGOON = EnvInfo("Palm Lagoon", 84, c1 = 0xFF3FC1FF.toInt(), c2 = 0xFFFFC46B.toInt())
val V_RIAD = EnvInfo("Riad Courtyard", 85, c1 = 0xFF2F6FD6.toInt(), c2 = 0xFFF2C46B.toInt())
val V_REEF = EnvInfo("Coral Reef", 86, c1 = 0xFF1FA6C7.toInt(), c2 = 0xFFFF8099.toInt())
val V_TRENCH = EnvInfo("Deep Trench", 87, c1 = 0xFF04101F.toInt(), c2 = 0xFF4DE6FF.toInt())
val V_MOSS = EnvInfo("Moss Garden", 88, c1 = 0xFF2E7D4F.toInt(), c2 = 0xFFD95B43.toInt())
val V_VOLCANO = EnvInfo("Volcano Crater", 89, c1 = 0xFF4D0D0D.toInt(), c2 = 0xFFFF8C1A.toInt())
val V_HAUNTED = EnvInfo("Haunted Courtyard", 90, c1 = 0xFF3D2E6B.toInt(), c2 = 0xFFFFBF4D.toInt())
val V_CRYSTAL = EnvInfo("Crystal Cave", 91, c1 = 0xFF5C3D8C.toInt(), c2 = 0xFF2DD4BF.toInt())
val V_LAB = EnvInfo("Robot Lab", 92, c1 = 0xFF0E2A33.toInt(), c2 = 0xFF2DD4BF.toInt())
val V_ROOFTOP = EnvInfo("Sky Rooftop", 93, c1 = 0xFF1F2A44.toInt(), c2 = 0xFF59B8FF.toInt())
val V_STATION = EnvInfo("Space Deck", 94, c1 = 0xFF0B1026.toInt(), c2 = 0xFF8A93AE.toInt())
val V_POND = EnvInfo("Lily Pond", 95, c1 = 0xFF7ED957.toInt(), c2 = 0xFFFF9EC4.toInt())
val V_FARM = EnvInfo("Farmyard", 96, c1 = 0xFFE8B04A.toInt(), c2 = 0xFFC0392B.toInt())
val V_BLOSSOM = EnvInfo("Blossom Garden", 97, c1 = 0xFFFFB7D5.toInt(), c2 = 0xFF8C6CE6.toInt())

/** A creature's worlds: its pictures (the ones that ship), then a plain colour and the user's own photo. */
fun worldsOf(vararg pics: EnvInfo): List<EnvInfo> = pics.filter { it.world in PHOTO_READY } + PLAIN_ENV + PHOTO_ENV

/**
 * The worlds offered for a creature in a given look. Glowy's Aquarium shows its tanks, Gromble adds its
 * code-drawn 3D worlds (they turn with the camera); every list ends with a plain colour and your photo.
 */
fun envsFor(info: CreatureInfo, style: Int): List<EnvInfo> = when {
    info.id == 5 && style == 3 -> AQUA_TANKS.mapIndexed { i, n -> EnvInfo(n, AQUA_WORLD0 + i, c1 = AQUA_TANK_COLORS[i * 2], c2 = AQUA_TANK_COLORS[i * 2 + 1]) } + PLAIN_ENV + PHOTO_ENV
    // Gromble: the painted worlds (he stands on them, the camera turns him), then the 3D worlds that turn with the camera
    info.id == 6 -> info.envs.filter { it.world in 0 until SOLID_WORLD } +
        ORBIT_ENVS.mapIndexed { i, n -> EnvInfo("$n 3D", ORBIT_WORLD0 + i, c1 = ORBIT_ENV_COLORS[i * 2], c2 = ORBIT_ENV_COLORS[i * 2 + 1]) } + PLAIN_ENV + PHOTO_ENV
    else -> info.envs
}

private val AQUA_TANK_COLORS = intArrayOf(
    0xFF2E7D4F.toInt(), 0xFF8EE03B.toInt(), 0xFF1FA6C7.toInt(), 0xFFFF8099.toInt(), 0xFF0A1F4D.toInt(), 0xFF4DE6FF.toInt(),
    0xFF3A2410.toInt(), 0xFFD9A15A.toInt(), 0xFFE8E0D0.toInt(), 0xFF5FB04F.toInt(), 0xFF02050C.toInt(), 0xFF2D5BFF.toInt(),
)
private val ORBIT_ENV_COLORS = intArrayOf(
    0xFF4D0D0D.toInt(), 0xFFFF8C1A.toInt(), 0xFF3D2E6B.toInt(), 0xFFFFBF4D.toInt(), 0xFF5C3D8C.toInt(), 0xFF2DD4BF.toInt(),
    0xFF0E2A44.toInt(), 0xFF9DE6FF.toInt(), 0xFFFFB3D9.toInt(), 0xFF8EF2D0.toInt(),
)

/** New photo worlds first; the old low-horizon pictures only stay until at least two new ones are ready. */
fun worlds(photo: List<EnvInfo>, old: List<EnvInfo>, keep: List<EnvInfo> = emptyList()): List<EnvInfo> {
    val fresh = photo.filter { it.world >= 60 && it.world in PHOTO_READY }
    val ready = if (fresh.size >= 2) fresh else photo.filter { it.world in PHOTO_READY }
    return ready + (if (ready.size < 2) old.filter { o -> ready.none { it.name == o.name } } else emptyList()) + keep
}

/** Creatures taken out of the app (Kitty, Leo, Kalboob, Nutty). A saved pick of one falls back to Mochi. */
val RETIRED_CREATURES = setOf(2, 3, 7, 12)

/**
 * Does the coat picker change anything for this creature in this style? Only the looks drawn from
 * coat colours use it; the others (Peachy, the 3D and photo looks, single-coat creatures) hide it.
 */
fun coatMatters(info: CreatureInfo, style: Int): Boolean {
    if (info.coats.size <= 1) return false
    return when (info.id) {
        1, 5 -> style == 0               // Jelly Bean, Lantern
        4 -> false                       // Kiwi: the painted ringneck is one bird
        9 -> true                        // Gloop Gang: how many
        else -> false
    }
}

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
    /** Looks the user can pick between (empty: just one). */
    val styles: List<String> = emptyList(),
)

val GLOWY_INFO = CreatureInfo(
    5, "Glowy", "Anglerfish, clownfish, betta, puffer or koi. Follows your finger.",
    "Drag: the lure follows you. Touch the water: little fish gather and it gulps them. Tap: the lure flashes.",
    0xFF80F2FF.toInt(), GLOWY_COATS.map { CoatInfo(it.name, it.body, it.glow) },
    worldsOf(V_TRENCH, V_REEF, V_MOSS),
    listOf(
        TrickInfo("feed", "Feed", "🐟"), TrickInfo("flash", "Flash the lure", "💡"), TrickInfo("puff", "Puff up", "🐡"),
        TrickInfo("loop", "Loop", "🌀"), TrickInfo("bubbles", "Bubbles", "🫧"), TrickInfo("yawn", "Big yawn", "🥱"),
        TrickInfo("stare", "Chase the lure", "👀"),
    ),
    styles = listOf("Lantern", "Crimson Betta", "Clownfish", "Aquarium"),
)

val GROMBLE_INFO = CreatureInfo(
    6, "Gromble", "Tries very hard to be evil. Fails adorably.",
    "Finger far away: he growls. Finger close: he panics. Poke him three times. Hold him: secretly loves it.",
    0xFFB380FF.toInt(), GROMBLE_COATS.map { CoatInfo(it.name, it.fur, it.horn) },
    // the World tab adds the code-drawn 3D worlds after these (see envsFor)
    worldsOf(V_VOLCANO, V_HAUNTED, V_CRYSTAL),
    listOf(
        TrickInfo("notify", "Knock", ""), TrickInfo("walk", "Walk", ""),
    ),
    styles = listOf("Reaper", "Mischief"),
)

val NUTTY_INFO = CreatureInfo(
    7, "Nutty", "Twitchy red squirrel. Always one second from panicking.",
    "Tap: it freezes and puffs up. Drag: it hops after you. Hold: offer an acorn and watch it run off to bury it.",
    0xFFF2AC6A.toInt(), NUTTY_COATS.map { CoatInfo(it.name, it.fur, it.light) },
    worlds(listOf(W_FOREST, W_PARK, P_AUTUMN, P_PARK), listOf(
        EnvInfo("Autumn Oak", 18, c1 = 0xFFE8A33A.toInt(), c2 = 0xFF6E4A30.toInt()),
        EnvInfo("City Park", 19, c1 = 0xFF7EC8FF.toInt(), c2 = 0xFF5FB04F.toInt()),
        EnvInfo("Snowy Pines", 20, c1 = 0xFFDDEBFF.toInt(), c2 = 0xFF2E6B5B.toInt()),
    )),
    listOf(
        TrickInfo("nibble", "Nibble", "🌰"), TrickInfo("stash", "Bury a nut", "🕳️"), TrickInfo("lookout", "Lookout", "👀"),
        TrickInfo("flick", "Tail flick", "🐿️"), TrickInfo("zoom", "Zoomies", "💨"), TrickInfo("catch", "Catch!", "🫴"),
        TrickInfo("spin", "Spin", "🌀"), TrickInfo("scratch", "Scratch", "🦶"), TrickInfo("feed", "Give an acorn", "🎁"),
        TrickInfo("drum", "Drum solo", "🥁"),
    ),
    styles = listOf("Acorn", "Nutmeg"),
)

val BOLT_INFO = CreatureInfo(
    8, "Bolt", "Five smart little robots with LED faces. Pick one.",
    "Tap it: it giggles (3 fast taps: it overheats). Drag it: you pick it up. Tap the floor: it goes there. Hold the screen: it scans your finger.",
    0xFF2DD4BF.toInt(), listOf(CoatInfo("Graphite", 0xFF231C35.toInt(), 0xFF2DD4BF.toInt())),
    worldsOf(V_LAB, V_ROOFTOP, V_STATION),
    listOf(
        TrickInfo("wave", "Wave", "👋"), TrickInfo("scan", "Scan", "🔍"), TrickInfo("jump", "Jump", "⬆️"), TrickInfo("glitch", "Glitch", "📺"),
        TrickInfo("nap", "Nap", "💤"), TrickInfo("faces", "Faces", "😎"), TrickInfo("look", "Look around", "👀"), TrickInfo("dance", "Cheer", "🎉"), TrickInfo("notify", "Notification", "🔔"),
    ),
    styles = BOT_LOOKS.map { it.name },
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
        PLAIN_ENV, PHOTO_ENV,
    ),
    listOf(
        TrickInfo("tag", "Tag", "🏃"), TrickInfo("tower", "Jelly tower", "🗼"), TrickInfo("conga", "Conga line", "💃"),
        TrickInfo("peekaboo", "Peekaboo", "🙈"), TrickInfo("bounce", "Bounce", "🔴"), TrickInfo("wave", "Mexican wave", "🌊"),
        TrickInfo("squish", "Squish all", "🫠"),
    ),
)

val SNOWY_INFO = CreatureInfo(
    SNOWY_ID, "Snowy", "Fluffy duckling. Quacks at everything, dreams of flying.",
    "Tap: quack-poke. Hold: cuddles. Drag to carry, fling to throw. Play music and it dances.",
    0xFFFFB45C.toInt(), listOf(CoatInfo("Snow white", 0xFFF4F1EA.toInt(), 0xFFFF9A2E.toInt())),
    worldsOf(V_POND, V_FARM, V_BLOSSOM),
    listOf(
        TrickInfo("quack", "Quack", "🦆"), TrickInfo("flap", "Flap", "🪽"), TrickInfo("fly", "Try to fly", "✈️"),
        TrickInfo("crumbs", "Bread crumbs", "🍞"), TrickInfo("splash", "Puddle", "💦"),
        TrickInfo("waddle", "Waddle", "🐾"), TrickInfo("sleep", "Nap", "💤"),
    ),
)

const val COSMO_ID = 11
const val LEO_ID = 12

val LEO_INFO = CreatureInfo(
    LEO_ID, "Leo", "Lion cub. Absolutely sure he is the king. His roar disagrees.",
    "Tap: he jumps. Hold: purrs into your hand. Rub: rolls over for a belly rub. Hold the ground: a butterfly to stalk.",
    0xFFFFB36B.toInt(), listOf(CoatInfo("Golden", 0xFFD9A15A.toInt(), 0xFF8A5A2B.toInt())),
    worlds(listOf(W_SAVANNA, W_WATERHOLE, P_SAVANNA, P_WATERHOLE), listOf(
        EnvInfo("Snow Day", 4, c1 = 0xFFDDEBFF.toInt(), c2 = 0xFF2E7D6B.toInt()),
        EnvInfo("Cozy Room", 5, c1 = 0xFFE0A070.toInt(), c2 = 0xFF8E3A50.toInt()),
    ), listOf(EnvInfo("Plain Colour", SOLID_WORLD, c1 = 0xFF000000.toInt(), c2 = 0xFF111111.toInt()))),
    listOf(
        TrickInfo("roar", "Roar!", "🦁"), TrickInfo("bigroar", "Real roar", "📢"), TrickInfo("pounce", "Pounce", "🐾"),
        TrickInfo("ball", "Ball", "⚽"), TrickInfo("belly", "Belly rub", "🫶"), TrickInfo("groom", "Wash up", "✨"),
        TrickInfo("beg", "Beg", "🙏"), TrickInfo("sleep", "Nap", "💤"),
    ),
)

val COSMO_INFO = CreatureInfo(
    COSMO_ID, "Cosmo", "Little astronaut, drifting forever among the stars. He's fine with it.",
    "Tap him: he gets knocked spinning. Tap space: a little push. Swipe across him: he floats off to your next home screen.",
    0xFF9DB8FF.toInt(), listOf(CoatInfo("Star suit", 0xFFF2F0F7.toInt(), 0xFFFFB36B.toInt())),
    listOf(EnvInfo("Deep Space", SOLID_WORLD, c1 = 0xFF000000.toInt(), c2 = 0xFF0A0614.toInt())),
    listOf(
        TrickInfo("faster", "Spin him", "🌀"), TrickInfo("wave", "Wave", "👋"),
        TrickInfo("reach", "Reach a star", "✨"), TrickInfo("thumbs", "Thumbs up", "👍"), TrickInfo("stretch", "Stretch", "🙆"),
    ),
)

val CREATURES: List<CreatureInfo> by lazy { BASE_CREATURES.filter { it.id !in RETIRED_CREATURES } + KIWI_INFO + GLOWY_INFO + GROMBLE_INFO + BOLT_INFO + GLOOP_INFO + SNOWY_INFO + COSMO_INFO }

/** Creatures that can also live in a home-screen widget (the 2D friends). */
val BUDDY_CREATURES: List<CreatureInfo> by lazy { CREATURES.filter { it.id != SNOWY_ID && it.id != COSMO_ID && it.id != LEO_ID && it.id != 6 } }

private val BASE_CREATURES = listOf(
    CreatureInfo(
        1, "Mochi", "Battery-juice bean. Don't take its jar.",
        "Tap Mochi. Hold it. Try stealing its jar while you charge.",
        0xFF7CF2A8.toInt(), MOCHI_COATS.map { CoatInfo(it.name, it.base, it.rim) },
        worldsOf(V_PLAYROOM, V_CANDY, V_CLOUD),
        listOf(TrickInfo("hello", "Say hi", "👋"), TrickInfo("bubble", "Blow a bubble", "🫧"), TrickInfo("star", "Catch a star", "⭐"), TrickInfo("party", "Party", "🎉"), TrickInfo("wobble", "Wobble", "🍮"), TrickInfo("giggle", "Giggle", "😆"), TrickInfo("stretch", "Big stretch", "🙆"),
            TrickInfo("shy", "Shy (Peachy)", "🙈"), TrickInfo("love", "Love (Peachy)", "💗"), TrickInfo("sleepy", "Sleepy (Peachy)", "💤"), TrickInfo("tumble", "Tumble (Peachy)", "🌀"), TrickInfo("notify", "Notification (Peachy)", "🔔")),
        styles = listOf("Jelly Bean", "Peachy"),
    ),
    CreatureInfo(
        2, "Kalboob", "Golden pup. Will fetch anything.",
        "Pet Kalboob. Hold for belly rubs. Hold the ground to throw a ball.",
        0xFFFFC46B.toInt(), PUP_COATS.map { CoatInfo(it.name, it.fur, it.collar) },
        worlds(listOf(W_SNOWYARD, W_GARDEN, P_BACKYARD, P_SNOWCABIN), listOf(
            EnvInfo("Snow Day", 4, c1 = 0xFFDDEBFF.toInt(), c2 = 0xFF2E7D6B.toInt()),
            EnvInfo("Cozy Room", 5, c1 = 0xFFE0A070.toInt(), c2 = 0xFF8E3A50.toInt()),
        )),
        listOf(TrickInfo("fetch", "Fetch", "🎾"), TrickInfo("paw", "Give paw", "🐾"), TrickInfo("belly", "Belly rub", "🫶"), TrickInfo("dig", "Dig", "🕳️"), TrickInfo("howl", "Howl", "🌕"), TrickInfo("zoomies", "Zoomies", "💨"), TrickInfo("yawn", "Yawn", "🥱"),
            TrickInfo("scratch", "Scratch", "🦶"), TrickInfo("bow", "Play bow", "🙇"), TrickInfo("bark", "Bark", "🐶"), TrickInfo("sniff", "Sniff", "👃")),
    ),
)

val KIWI_INFO = CreatureInfo(
    4, "Kiwi", "Talks, whistles, headbangs. Teach it words.",
    "Tap it: squawk. Drag it off the branch and let go: it flies a lap and lands. Swipe your pages: it takes off.",
    0xFF7BE08A.toInt(), KIWI_SPECIES.map { CoatInfo(it.name, it.body, it.face) },
    worldsOf(V_CANOPY, V_LAGOON, V_RIAD),
    listOf(
        TrickInfo("talk", "Say something", ""), TrickInfo("whistle", "Whistle", ""), TrickInfo("squawk", "Squawk!", ""),
        TrickInfo("fly", "Fly a lap", ""), TrickInfo("hang", "Upside-down", ""), TrickInfo("sidestep", "Side step", ""),
        TrickInfo("workout", "Flap", ""), TrickInfo("shakeoff", "Shake off", ""), TrickInfo("curious", "Curious", ""),
        TrickInfo("oneleg", "One leg", ""), TrickInfo("puffup", "Puff up", ""), TrickInfo("stretch", "Wing stretch", ""),
    ),
    styles = listOf("Ringneck"),
)

fun creatureInfo(id: Int): CreatureInfo? = when (id) { SHADOW_ID -> SHADOW_INFO; STATUE_ID -> STATUE_INFO; else -> CREATURES.firstOrNull { it.id == id } }

const val STATUE_ID = 14

/** Pulsify Pharaohs: its own section — a 3D statue that flies in like a super wallpaper. */
val STATUE_INFO = CreatureInfo(
    STATUE_ID, "Pharaohs", "Gold, stone and gods in the dark. Every home page shows a new angle.",
    "Turn the screen on: it zooms in. Unlock: the camera flies around it. Swipe your pages: a new angle each time. Play music: the gold icons around it light up.",
    0xFFFFB36B.toInt(), listOf(CoatInfo("Pitch black", 0xFF000000.toInt(), 0xFFFFB36B.toInt())),
    listOf(EnvInfo("Pitch Black", SOLID_WORLD, c1 = 0xFF000000.toInt(), c2 = 0xFF000000.toInt())),
    listOf(
        TrickInfo("wake", "Screen on", "🔆"), TrickInfo("unlock", "Unlock", "🔓"), TrickInfo("next", "Next page", "▶️"),
        TrickInfo("prev", "Page back", "◀️"), TrickInfo("fly", "Fly in", "🚁"),
    ),
    styles = STATUE_LOOKS.map { it.name },
)

/** Pulsify Shadows: its own section — an evil grin in the dark, eyes and teeth only, true AMOLED black. */
val SHADOW_INFO = CreatureInfo(
    SHADOW_ID, "Shadows", "Eyes and a grin in the dark. Nothing else lights up.",
    "Touch anywhere: it looks there. Poke an eye (3× = rage). Tap the teeth: chomp. Tap the face: it laughs. Hold the screen: it stares. Leave it alone: it lurks.",
    0xFFFF2EC4.toInt(), listOf(CoatInfo("Pitch black", 0xFF000000.toInt(), 0xFFFF2EC4.toInt())),
    listOf(EnvInfo("Pitch Black", SOLID_WORLD, c1 = 0xFF000000.toInt(), c2 = 0xFF000000.toInt())),
    listOf(
        TrickInfo("laugh", "Laugh", "😈"), TrickInfo("chomp", "Chomp", "🦷"), TrickInfo("rage", "Rage", "😡"), TrickInfo("stare", "Stare", "👁️"),
        TrickInfo("vanish", "Vanish", "💨"), TrickInfo("jumpscare", "Jumpscare", "⚡"), TrickInfo("roll", "Eye roll", "🙄"), TrickInfo("lurk", "Lurk", "😶"), TrickInfo("notify", "Notification", "🔔"),
    ),
    styles = SHADOW_LOOKS.map { it.name },
)
