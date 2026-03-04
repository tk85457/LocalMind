package com.localmind.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    fun nextPage() {
        viewModelScope.launch {
            if (_currentPage.value < 2) {
                _currentPage.value += 1
            } else {
                markOnboardingComplete()
                _isComplete.value = true
            }
        }
    }

    fun previousPage() {
        viewModelScope.launch {
            if (_currentPage.value > 0) {
                _currentPage.value -= 1
            }
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            markOnboardingComplete()
            _isComplete.value = true
        }
    }

    private suspend fun markOnboardingComplete() {
        settingsRepository.setOnboardingCompleted(true)
    }
}
