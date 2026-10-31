package com.example.picomatch3vr.p3a7c9f1.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::gridFeedbackPresentation)
    }
}
