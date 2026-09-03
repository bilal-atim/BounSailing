package com.bilal.marmarisnav.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilal.marmarisnav.MarmarisNavApp
import com.bilal.marmarisnav.data.ChartTheme
import com.bilal.marmarisnav.location.NavigationService
import com.bilal.marmarisnav.ui.theme.MarmarisNavTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = application as MarmarisNavApp

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) NavigationService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: ChartViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()

            // The helm needs the chart visible while under way; the setting lets
            // the user turn it off when the phone is only along for the ride.
            if (settings.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            MarmarisNavTheme(chartTheme = settings.theme) {
                BounSailingRoot(
                    chartViewModel = viewModel,
                    onRequestPermissions = ::requestPermissions,
                )
            }
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(needed.toTypedArray())
    }

    override fun onStop() {
        super.onStop()
        // Keep the service alive only when it still has work: an active anchor
        // watch, a recording track, or a live navigation target. Otherwise the
        // GPS is released as soon as the chart is out of sight.
        lifecycleScope.launch {
            val settings = app.settings.settings.first()
            val busy = settings.anchorSet ||
                settings.recordingTrackId != null ||
                settings.activeWaypointId != null ||
                settings.activeRouteId != null
            if (!busy && !isChangingConfigurations) {
                NavigationService.stop(this@MainActivity)
            }
        }
    }
}
