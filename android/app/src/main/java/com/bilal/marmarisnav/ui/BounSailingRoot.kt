package com.bilal.marmarisnav.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilal.marmarisnav.ui.library.LibraryScreen
import com.bilal.marmarisnav.ui.library.LibraryViewModel
import com.bilal.marmarisnav.ui.notes.NotesScreen

enum class Tab(val label: String, val icon: ImageVector) {
    LIBRARY("Kütüphane", Icons.AutoMirrored.Filled.MenuBook),
    CHART("Harita", Icons.Filled.Map),
    NOTES("Notlar", Icons.Filled.EditNote),
}

@Composable
fun BounSailingRoot(
    chartViewModel: ChartViewModel,
    onRequestPermissions: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    val libraryViewModel: LibraryViewModel = viewModel()

    // The library tab owns a back stack of its own; hardware back walks it before
    // it means anything to the rest of the app.
    BackHandler(enabled = tab == Tab.LIBRARY) {
        libraryViewModel.back()
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxSize()) {
            // The chart stays composed across tab switches so MapLibre is not torn
            // down and rebuilt — that would drop the camera and reload the style.
            // The other tabs draw opaque surfaces over it.
            MarmarisNavRoot(
                viewModel = chartViewModel,
                onRequestPermissions = onRequestPermissions,
            )

            if (tab != Tab.CHART) {
                // The surface itself covers the chart edge to edge, including
                // behind the status bar; the content inside is inset below it.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val inset = Modifier.fillMaxSize().statusBarsPadding()
                    when (tab) {
                        Tab.LIBRARY -> LibraryScreen(libraryViewModel, inset)
                        Tab.NOTES -> NotesScreen(inset)
                        Tab.CHART -> Unit
                    }
                }
            }
        }

        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            for (entry in Tab.entries) {
                NavigationBarItem(
                    selected = tab == entry,
                    onClick = {
                        // Tapping the tab you are already on returns it to its root.
                        if (tab == entry && entry == Tab.LIBRARY) {
                            libraryViewModel.resetToHome()
                        }
                        tab = entry
                    },
                    icon = { Icon(entry.icon, contentDescription = entry.label) },
                    label = { Text(entry.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}
