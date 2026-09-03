package com.bilal.marmarisnav.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bilal.marmarisnav.library.Library
import com.bilal.marmarisnav.library.LibrarySearch
import com.bilal.marmarisnav.library.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the library tab currently is. The tab keeps its own back stack. */
sealed interface LibraryRoute {
    data object Home : LibraryRoute
    data class TopicView(val id: String) : LibraryRoute
    data class SourceView(val id: String) : LibraryRoute
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val _library = MutableStateFlow<Library?>(null)
    val library: StateFlow<Library?> = _library.asStateFlow()

    private var search: LibrarySearch? = null

    private val _stack = MutableStateFlow<List<LibraryRoute>>(listOf(LibraryRoute.Home))
    val stack: StateFlow<List<LibraryRoute>> = _stack.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchHit>>(emptyList())
    val results: StateFlow<List<SearchHit>> = _results.asStateFlow()

    /** Category the user has open on the home screen; null means all collapsed. */
    private val _expandedCategory = MutableStateFlow<String?>(null)
    val expandedCategory: StateFlow<String?> = _expandedCategory.asStateFlow()

    val route: LibraryRoute get() = _stack.value.last()

    init {
        viewModelScope.launch {
            // Parsing 37 topics and 22 source documents off the main thread keeps
            // the first frame of the tab from stalling.
            val loaded = withContext(Dispatchers.IO) { Library.load(getApplication()) }
            search = LibrarySearch(loaded)
            _library.value = loaded
            if (_query.value.isNotBlank()) runSearch(_query.value)
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        runSearch(value)
    }

    fun clearQuery() = setQuery("")

    private fun runSearch(value: String) {
        val engine = search
        _results.value = if (value.isBlank() || engine == null) emptyList() else engine.search(value)
    }

    fun toggleCategory(id: String) {
        _expandedCategory.value = if (_expandedCategory.value == id) null else id
    }

    fun openTopic(id: String) = push(LibraryRoute.TopicView(id))

    fun openSource(id: String) = push(LibraryRoute.SourceView(id))

    private fun push(route: LibraryRoute) {
        // Re-tapping the topic you are already on should not stack duplicates.
        if (_stack.value.last() == route) return
        _stack.value = _stack.value + route
    }

    /** Returns false when the library tab is already at its root. */
    fun back(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.value = _stack.value.dropLast(1)
        return true
    }

    fun resetToHome() {
        _stack.value = listOf(LibraryRoute.Home)
    }
}
