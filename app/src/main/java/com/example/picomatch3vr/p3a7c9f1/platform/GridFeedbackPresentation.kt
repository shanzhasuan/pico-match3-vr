package com.example.picomatch3vr.p3a7c9f1.platform

import androidx.compose.foundation.layout.Box
import com.example.picomatch3vr.p3a7c9f1.ui.campaign.GameplayStage
import com.example.picomatch3vr.p3a7c9f1.ui.campaign.SnailClarityLayer
import com.example.picomatch3vr.p3a7c9f1.ui.campaign.StraightSpecialPresentation
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun gridFeedbackPresentation(scope: SpatialAppScope) = with(scope) {
    DefaultStage {
        PicoTheme {
            Box {
                GameplayStage()
                SnailClarityLayer()
                StraightSpecialPresentation()
            }
        }
    }
}
