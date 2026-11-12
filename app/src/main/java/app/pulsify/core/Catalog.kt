package app.pulsify.core

/** Everything shown in the gallery. Only the Night Sky world is live in milestone 1. */

data class SkyVariant(
    val id: Int,
    val name: String,
    val tagline: String,
    val accent1: Int,
    val accent2: Int,
)

val SKY_VARIANTS = listOf(
    SkyVariant(0, "Night Sky", "Quiet stars, a faint milky band", 0xFFCDB4FF.toInt(), 0xFFFFB36B.toInt()),
    SkyVariant(1, "Aurora", "Curtains of light that dance to the mids", 0xFF2DD4BF.toInt(), 0xFF9D4EDD.toInt()),
    SkyVariant(2, "Nebula", "Slow clouds that swell with the bass", 0xFFFF5FA2.toInt(), 0xFF5B8CFF.toInt()),
)

enum class Kind(val label: String) { CREATURE("Creatures"), WORLD("Worlds"), LOOP("Loops") }

data class ComingSoon(
    val name: String,
    val kind: Kind,
    val line: String,
    val c1: Int,
    val c2: Int,
    val milestone: Int,
)

val COMING = listOf(
    ComingSoon("Sparky", Kind.CREATURE, "Bat with goggles and a sparkler.", 0xFFFF9F43.toInt(), 0xFF6C5CE7.toInt(), 8),
    ComingSoon("Cosmic loops", Kind.LOOP, "Earth, Saturn, black hole, nebula — seamless.", 0xFF5B8CFF.toInt(), 0xFFFFB36B.toInt(), 9),
    ComingSoon("Liquid Obsidian", Kind.WORLD, "Black chrome ferrofluid that spikes to bass.", 0xFFB8B8C8.toInt(), 0xFF9D4EDD.toInt(), 10),
)

data class Discovery(val id: String, val title: String, val hint: String)

val DISCOVERIES = listOf(
    Discovery("tap", "First ripple", "Tap the sky"),
    Discovery("catch", "Star catcher", "Tap a shooting star before it fades"),
    Discovery("wish", "Make a wish", "Hold your finger still on the sky"),
    Discovery("shower", "Meteor shower", "Swipe really fast"),
    Discovery("groove", "In the groove", "Play music with music reaction on"),
    Discovery("owl", "Night owl", "Visit the sky after midnight"),
    Discovery("tickle", "Ticklish", "Tap Mochi three times, fast"),
    Discovery("pet", "Best friends", "Hold your finger on Mochi"),
    Discovery("jar_thief", "Jar thief", "Drag Mochi's battery jar away"),
    Discovery("rescue", "Got it back", "Let Mochi catch its jar"),
    Discovery("star_snack", "Star snack", "Watch Mochi eat a falling star"),
    Discovery("bird", "Birdwatcher", "Wait for a little visitor"),
    Discovery("fetch", "Good boy", "Hold the sky to throw Kalboob a ball"),
    Discovery("belly_rub", "Belly rub", "Hold your finger on Kalboob"),
    Discovery("give_paw", "Paw five", "Wait for Kalboob to give you its paw"),
    Discovery("butterfly", "Butterfly kiss", "Watch a butterfly land on Kalboob's nose"),
    Discovery("dig", "Digger", "Catch Kalboob digging"),
    Discovery("howl", "Awoooo", "Play a song with long notes"),
    Discovery("zoomies", "Zoomies", "Swipe really fast past Kalboob"),
    Discovery("laser", "Laser pointer", "Drag a finger near Kitty"),
    Discovery("pounce", "Got it!", "Hold the laser dot still"),
    Discovery("purr", "Purr engine", "Hold your finger on Kitty"),
    Discovery("knock", "Gravity check", "Catch Kitty pushing the cup off"),
    Discovery("cucumber", "THE CUCUMBER", "Wait for a vegetable to appear"),
    Discovery("box", "If I fits", "Catch Kitty in a box"),
)
