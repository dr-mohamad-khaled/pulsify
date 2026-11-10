package app.pulsify.core

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Single source of truth for everything the wallpaper and the app share.
 * The wallpaper runs in the same process and listens for changes, so "Set as live
 * wallpaper" is picked up instantly without re-selecting the wallpaper.
 */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("pulsify", Context.MODE_PRIVATE)

    /** 0 = Night Sky only, 1 = Mochi. */
    var character: Int
        get() = sp.getInt(K_CHAR, 1)
        set(v) = sp.edit().putInt(K_CHAR, v).apply()

    /** Each creature remembers its own coat. */
    var coat: Int
        get() = sp.getInt(K_COAT + "_" + character, 0)
        set(v) = sp.edit().putInt(K_COAT + "_" + character, v).apply()

    /** Each creature remembers its own environment (index into its env list). */
    var env: Int
        get() = sp.getInt(K_ENV + "_" + character, 0)
        set(v) = sp.edit().putInt(K_ENV + "_" + character, v).apply()

    var variant: Int
        get() = sp.getInt(K_VARIANT, 0)
        set(v) = sp.edit().putInt(K_VARIANT, v).apply()

    /** 0 = follow the wallpaper's own colours, otherwise an ARGB accent. */
    var accent: Int
        get() = sp.getInt(K_ACCENT, 0)
        set(v) = sp.edit().putInt(K_ACCENT, v).apply()

    var brightness: Float
        get() = sp.getFloat(K_BRIGHT, 1f)
        set(v) = sp.edit().putFloat(K_BRIGHT, v).apply()

    var musicReact: Boolean
        get() = sp.getBoolean(K_MUSIC, false)
        set(v) = sp.edit().putBoolean(K_MUSIC, v).apply()

    var kiwiWords: String
        get() = sp.getString(K_WORDS, "") ?: ""
        set(v) = sp.edit().putString(K_WORDS, v.take(400)).apply()

    var batterySaver: Boolean
        get() = sp.getBoolean(K_SAVER, false)
        set(v) = sp.edit().putBoolean(K_SAVER, v).apply()

    var shuffleOnUnlock: Boolean
        get() = sp.getBoolean(K_SHUFFLE, false)
        set(v) = sp.edit().putBoolean(K_SHUFFLE, v).apply()

    val bondXp: Int get() = sp.getInt(K_XP, 0)
    val streak: Int get() = sp.getInt(K_STREAK, 0)
    val discoveries: Set<String> get() = sp.getStringSet(K_DISC, emptySet()) ?: emptySet()

    fun addXp(amount: Int) {
        sp.edit().putInt(K_XP, bondXp + amount).apply()
    }

    /** Returns true the first time a discovery is made. */
    fun discover(id: String): Boolean {
        val cur = discoveries
        if (id in cur) return false
        sp.edit()
            .putStringSet(K_DISC, cur + id)
            .putInt(K_XP, bondXp + 25)
            .apply()
        return true
    }

    /** Daily check-in. Returns the XP awarded today (0 if already checked in). */
    fun checkIn(today: LocalDate = LocalDate.now()): Int {
        val last = sp.getLong(K_LASTDAY, -1L)
        val t = today.toEpochDay()
        if (last == t) return 0
        val newStreak = if (last == t - 1) streak + 1 else 1
        val reward = 10 + (newStreak.coerceAtMost(7) - 1) * 5
        sp.edit()
            .putLong(K_LASTDAY, t)
            .putInt(K_STREAK, newStreak)
            .putInt(K_XP, bondXp + reward)
            .apply()
        return reward
    }

    companion object {
        const val K_CHAR = "character"
        const val K_COAT = "coat"
        const val K_ENV = "env"
        const val K_VARIANT = "variant"
        const val K_ACCENT = "accent"
        const val K_BRIGHT = "brightness"
        const val K_MUSIC = "musicReact"
        const val K_SAVER = "batterySaver"
        const val K_SHUFFLE = "shuffleOnUnlock"
        const val K_XP = "bondXp"
        const val K_STREAK = "streak"
        const val K_LASTDAY = "lastDay"
        const val K_DISC = "discoveries"
        const val K_WORDS = "kiwiWords"

        fun level(xp: Int): Int = (kotlin.math.sqrt(xp / 20.0)).toInt() + 1
        fun xpForLevel(level: Int): Int = (level - 1) * (level - 1) * 20
    }
}
