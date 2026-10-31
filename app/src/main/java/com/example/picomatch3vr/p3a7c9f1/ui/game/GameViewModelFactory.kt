package com.example.picomatch3vr.p3a7c9f1.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.picomatch3vr.p3a7c9f1.data.repository.FakeMatch3Repository

class GameViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GameViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return GameViewModel(
            repository = FakeMatch3Repository(),
        ) as T
    }
}
