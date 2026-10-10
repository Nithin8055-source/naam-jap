package com.naamjap.counterapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naamjap.counterapp.data.settings.ThemePreferencesRepository
import com.naamjap.counterapp.ui.theme.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ThemeUiState(
    val choice: ThemeChoice = ThemeChoice.SYSTEM,
    val isLoaded: Boolean = false
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val repository: ThemePreferencesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ThemeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.themeChoice
                .catch { emit(ThemeChoice.SYSTEM) }
                .collect { choice ->
                    _uiState.update { it.copy(choice = choice, isLoaded = true) }
                }
        }
    }

    fun selectTheme(choice: ThemeChoice) {
        val previousChoice = _uiState.value.choice
        _uiState.update { it.copy(choice = choice) }
        viewModelScope.launch {
            try {
                repository.setThemeChoice(choice)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { current ->
                    if (current.choice == choice) current.copy(choice = previousChoice) else current
                }
            }
        }
    }
}
