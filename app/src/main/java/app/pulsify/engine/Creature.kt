package app.pulsify.engine

/** What the render loop needs from any character. Each character is its own class and shader. */
interface Creature {
    var listener: SceneListener?
    var bottomInset: Float
    val busy: Boolean
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    /** Touch in normalised screen coordinates (0..1, y down). Return true when the creature used it. */
    fun touchDown(nx: Float, ny: Float): Boolean
    fun touchMove(nx: Float, ny: Float): Boolean
    fun touchUp(): Boolean
    fun tapAt(nx: Float, ny: Float): Boolean
    fun longPressAt(nx: Float, ny: Float): Boolean
    fun greet()
    /** The screen came on ([locked]: over the lock screen). */
    fun screenOn(locked: Boolean) {}
    /** Where the ground is in the current world picture (uv), or NaN for the default floor. */
    fun setGround(y: Float) {}
    /** Sideways home spot chosen on the user's own photo (-0.5..0.5 of the screen width, 0 = centre). */
    fun setHomeX(x: Float) {}
    /** Plays a named trick from the Studio's Tricks tab. Returns false for unknown ids. */
    fun perform(trick: String): Boolean = false
    /** The home screen was swiped to another page and settled ([dir] = -1 left, 1 right). */
    fun swiped(dir: Float, fast: Boolean) { greet() }
    /** Minimum time between two swipe reactions. Creatures shouldn't repeat a reaction on every flick;
     *  camera scenes (the statues) must answer every single swipe. */
    val swipeCooldownMs: Long get() = 1500L
    fun update(dt: Float, input: SceneInput)
    fun draw(input: SceneInput)
    fun release()
}

/** Seconds between idle tricks: always at least 3 tricks a minute, even with long tricks. */
const val TRICK_GAP_MIN = 4f
const val TRICK_GAP_RANGE = 4f
