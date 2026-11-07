package app.pulsify

import android.app.Application
import app.pulsify.core.Pulse

class PulsifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app.pulsify.core.CrashLog.install(this)
        Pulse.init(this)
    }
}
