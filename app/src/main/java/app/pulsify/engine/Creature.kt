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
    fun update(dt: Float, input: SceneInput)
    fun draw(input: SceneInput)
    fun release()
}
