package com.gameoptimizer.app.optimizer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class InstalledGame(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * Detecta juegos instalados en el dispositivo usando la categoría oficial
 * de la app (ApplicationInfo.CATEGORY_GAME) y, como respaldo, el flag legacy
 * de versiones antiguas de Android que marcaban apps como "isGame".
 */
object GameDetector {

    fun getInstalledGames(context: Context): List<InstalledGame> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val games = mutableListOf<InstalledGame>()

        for (app in apps) {
            if (isGame(app) && hasLaunchIntent(pm, app.packageName)) {
                games.add(
                    InstalledGame(
                        packageName = app.packageName,
                        label = app.loadLabel(pm).toString(),
                        icon = try { app.loadIcon(pm) } catch (e: Exception) { null }
                    )
                )
            }
        }

        return games.sortedBy { it.label.lowercase() }
    }

    private fun hasLaunchIntent(pm: PackageManager, packageName: String): Boolean {
        return pm.getLaunchIntentForPackage(packageName) != null
    }

    private fun isGame(app: ApplicationInfo): Boolean {
        // No incluir apps de sistema (lanzadores, ajustes, etc.)
        val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (isSystemApp) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.category == ApplicationInfo.CATEGORY_GAME
        } else {
            @Suppress("DEPRECATION")
            (app.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        }
    }
}
