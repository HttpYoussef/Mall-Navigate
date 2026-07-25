package com.example.mallar.ui.destination

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mallar.data.Place
import com.example.mallar.data.PlaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DestinationUiState(
    val allPlaces: List<Place> = emptyList(),
    val displayedPlaces: List<Place> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryKey: String = "",
    val isLoading: Boolean = false
)

class DestinationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DestinationUiState(isLoading = true))
    val uiState: StateFlow<DestinationUiState> = _uiState.asStateFlow()

    init {
        loadPlaces()
    }

    private fun loadPlaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val places = PlaceRepository.load(getApplication())
            _uiState.update { it.copy(allPlaces = places, isLoading = false) }
            applyFilter()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    fun onCategorySelected(categoryKey: String) {
        _uiState.update { it.copy(selectedCategoryKey = categoryKey) }
        applyFilter()
    }

    private fun applyFilter() {
        val state = _uiState.value
        val trimmedQuery = state.searchQuery.trim()
        
        var filtered = if (trimmedQuery.isBlank()) {
            state.allPlaces
        } else {
            state.allPlaces.filter { place ->
                place.brand.orEmpty().contains(trimmedQuery, ignoreCase = true)
            }
        }

        if (state.selectedCategoryKey.isNotBlank()) {
            filtered = filtered.filter { PlaceRepository.matchesCategory(it, state.selectedCategoryKey) }
        }

        _uiState.update { it.copy(displayedPlaces = filtered) }
    }
}
