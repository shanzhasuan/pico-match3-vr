package com.example.picomatch3vr.p3a7c9f1.ui.gameplay

import com.example.picomatch3vr.p3a7c9f1.ui.game.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.picomatch3vr.p3a7c9f1.data.repository.FakeMatch3Repository

class GameplayViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GameplayViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return GameplayViewModel(
            repository = FakeMatch3Repository(),
        ) as T
    }
}
