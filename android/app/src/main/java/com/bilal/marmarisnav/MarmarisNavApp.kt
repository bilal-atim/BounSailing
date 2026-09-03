package com.bilal.marmarisnav

import android.app.Application
import com.bilal.marmarisnav.data.ChartManifest
import com.bilal.marmarisnav.data.SettingsRepository
import com.bilal.marmarisnav.database.AppDatabase
import com.bilal.marmarisnav.navigation.NavigationEngine
import com.bilal.marmarisnav.service.NavigationNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

/**
 * Process-wide container.
 *
 * The navigation engine lives here rather than in the service or a ViewModel so
 * that state survives the service being stopped, the activity being recreated,
 * or the user rotating the tablet mid-passage.
 */
class MarmarisNavApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val manifest: ChartManifest by lazy { ChartManifest.load(this) }

    val engine: NavigationEngine by lazy {
        NavigationEngine(
            scope = appScope,
            settingsRepository = settings,
            waypointDao = database.waypointDao(),
            routeDao = database.routeDao(),
            trackDao = database.trackDao(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        // No tile server is contacted, but MapLibre still requires initialisation
        // before any MapView is inflated.
        MapLibre.getInstance(this)
        NavigationNotifications.createChannels(this)

        appScope.launch {
            settings.settings.collect { engine.updateSettings(it) }
        }
    }
}
