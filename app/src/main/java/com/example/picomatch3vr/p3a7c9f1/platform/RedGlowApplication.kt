package com.example.picomatch3vr.p3a7c9f1.platform

import android.app.Application
import com.example.picomatch3vr.p3a7c9f1.ui.campaign.StartupTransition
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.launch

class RedGlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch {
            DefaultStage {
                PicoTheme {
                    StartupTransition()
                }
            }
        }
    }
}
