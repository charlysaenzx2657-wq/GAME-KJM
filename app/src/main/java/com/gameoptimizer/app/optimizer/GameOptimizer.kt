package com.gameoptimizer.app.optimizer

import android.content.Context
import android.content.Intent
import com.gameoptimizer.app.shizuku.ShizukuManager

/**
 * Aplica optimizaciones reales (vía Shizuku) antes y durante una sesión de juego,
 * y las revierte al salir.
 */
class GameOptimizer(private val context: Context) {

    data class OptimizationResult(
        val killedApps: List<String>,
        val animationsDisabled: Boolean,
        val log: List<String>
    )

    // Apps que casi nunca hace falta tener corriendo mientras juegas.
    // Se puede hacer configurable desde la UI más adelante.
    private val defaultKillTargets = listOf(
        "com.facebook.katana",
        "com.facebook.orca",
        "com.instagram.android",
        "com.whatsapp",
        "com.spotify.music",
        "com.google.android.youtube"
    )

    /**
     * Optimiza el sistema antes de lanzar el juego.
     * Devuelve un resumen de lo que se hizo, para poder revertirlo después.
     */
    fun optimizeForGame(packageToLaunch: String, extraAppsToKill: List<String> = emptyList()): OptimizationResult {
        val log = mutableListOf<String>()
        val killed = mutableListOf<String>()

        // 1. Matar procesos en background que consumen RAM/CPU (menos que el propio juego)
        val targets = (defaultKillTargets + extraAppsToKill).distinct().filter { it != packageToLaunch }
        for (pkg in targets) {
            val result = ShizukuManager.runShellCommand("am force-stop $pkg")
            if (!result.startsWith("ERROR")) {
                killed.add(pkg)
                log.add("Cerrado: $pkg")
            }
        }

        // 2. Desactivar animaciones del sistema (menos overhead de compositor mientras juegas)
        ShizukuManager.runShellCommand("settings put global window_animation_scale 0")
        ShizukuManager.runShellCommand("settings put global transition_animation_scale 0")
        ShizukuManager.runShellCommand("settings put global animator_duration_scale 0")
        log.add("Animaciones del sistema desactivadas")

        // 3. Intentar forzar el modo de rendimiento alto si el fabricante lo expone.
        // NOTA: esto varía por fabricante/chipset y puede no tener efecto en todos los equipos
        // (en Unisoc T606 el soporte es limitado comparado con Snapdragon/Exynos).
        val perfResult = ShizukuManager.runShellCommand("cmd power set-fixed-performance-mode-enabled true")
        if (!perfResult.startsWith("ERROR")) {
            log.add("Modo de rendimiento fijo solicitado al sistema")
        }

        // 4. Reducir el brillo de notificaciones/vibración durante el juego (opcional, no crítico)
        ShizukuManager.runShellCommand("settings put system notification_light_pulse 0")

        return OptimizationResult(killed, true, log)
    }

    /**
     * Revierte los cambios de sistema al salir del juego (animaciones, modo rendimiento).
     */
    fun restoreDefaults() {
        ShizukuManager.runShellCommand("settings put global window_animation_scale 1")
        ShizukuManager.runShellCommand("settings put global transition_animation_scale 1")
        ShizukuManager.runShellCommand("settings put global animator_duration_scale 1")
        ShizukuManager.runShellCommand("cmd power set-fixed-performance-mode-enabled false")
    }

    /**
     * Lanza el juego después de optimizar.
     */
    fun launchGame(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }
}
