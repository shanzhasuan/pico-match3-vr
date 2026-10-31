package com.example.picomatch3vr.p3a7c9f1

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.example.picomatch3vr.p3a7c9f1.ui.campaign.GameplayStage
import com.example.picomatch3vr.p3a7c9f1.ui.campaign.SnailClarityLayer
import androidx.compose.foundation.layout.Box

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                Box {
                    GameplayStage()
                    SnailClarityLayer()
                }
            }
        }
    }
