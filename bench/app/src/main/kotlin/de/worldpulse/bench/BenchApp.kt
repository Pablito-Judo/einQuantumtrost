package de.worldpulse.bench

import android.app.Application
import de.worldpulse.bench.tests.RunMarker

class BenchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ResultLog.init(this)
        BenchService.createChannel(this)
        // Wurde ein Langlauf (RAM-Test, Dienst-Test) durch Android beendet? -> protokollieren.
        RunMarker.checkOnStart(this, Device(this))
    }
}
