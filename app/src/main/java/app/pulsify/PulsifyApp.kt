package app.pulsify

import android.app.Application
import app.pulsify.core.Pulse

class PulsifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Pulse.init(this)
    }
}
