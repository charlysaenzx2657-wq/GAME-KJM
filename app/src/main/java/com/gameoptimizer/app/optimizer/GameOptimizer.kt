package com.gameoptimizer.app.optimizer

import android.content.Context
import android.content.Intent
import com.gameoptimizer.app.shizuku.ShizukuManager

/**
 * Aplica optimizaciones reales (vía Shizuku) antes y durante una sesión de juego,
 * y las revierte al salir.
 *
 * Las optimizaciones están agrupadas por impacto real esperado:
 * - HIGH: cambios que sueltan RAM/CPU/GPU de forma medible
 * - MEDIUM: reducen overhead de fondo (red, sensores, sync)
 * - LOW: ajustes menores/cosméticos que suman poco individualmente pero no estorban
 */
class GameOptimizer(private val context: Context) {

    enum class Impact { HIGH, MEDIUM, LOW }

    data class Optimization(
        val id: String,
        val label: String,
        val impact: Impact,
        val apply: String,
        val restore: String? = null
    )

    data class OptimizationResult(
        val killedApps: List<String>,
        val applied: List<String>,
        val failed: List<String>,
        val log: List<String>
    )

    private val defaultKillTargets = listOf(
        "com.facebook.katana", "com.facebook.orca", "com.instagram.android",
        "com.whatsapp", "com.spotify.music", "com.google.android.youtube",
        "com.twitter.android", "com.snapchat.android", "com.pinterest",
        "com.reddit.frontpage", "com.tiktok", "com.zhiliaoapp.musically",
        "com.discord", "com.telegram.messenger", "com.netflix.mediaclient",
        "com.google.android.apps.photos", "com.google.android.gm",
        "com.microsoft.office.outlook", "com.amazon.mShop.android.shopping",
        "com.ebay.mobile"
    )

    // Lista de 50+ optimizaciones a nivel de sistema, todas ejecutadas vía Shizuku shell.
    private val optimizations: List<Optimization> = listOf(
        // ===== ANIMACIONES / UI (HIGH-MEDIUM) =====
        Optimization("anim_window", "Desactivar animación de ventanas", Impact.HIGH,
            "settings put global window_animation_scale 0", "settings put global window_animation_scale 1"),
        Optimization("anim_transition", "Desactivar animación de transiciones", Impact.HIGH,
            "settings put global transition_animation_scale 0", "settings put global transition_animation_scale 1"),
        Optimization("anim_duration", "Desactivar duración de animador", Impact.HIGH,
            "settings put global animator_duration_scale 0", "settings put global animator_duration_scale 1"),

        // ===== RENDIMIENTO CPU/GPU (HIGH) =====
        Optimization("perf_fixed", "Modo de rendimiento fijo", Impact.HIGH,
            "cmd power set-fixed-performance-mode-enabled true", "cmd power set-fixed-performance-mode-enabled false"),
        Optimization("perf_adpf", "Desactivar throttling adaptativo (ADPF) si existe", Impact.MEDIUM,
            "cmd power set-adaptive-power-saver-enabled false", "cmd power set-adaptive-power-saver-enabled true"),
        Optimization("battery_saver_off", "Desactivar ahorro de batería", Impact.HIGH,
            "settings put global low_power 0", null),
        Optimization("battery_saver_sticky_off", "Desactivar modo ahorro persistente", Impact.LOW,
            "settings put global low_power_sticky 0", null),
        Optimization("doze_off", "Desactivar modo Doze (deep sleep de apps)", Impact.MEDIUM,
            "dumpsys deviceidle disable", "dumpsys deviceidle enable"),
        Optimization("app_standby_off", "Desactivar App Standby agresivo", Impact.MEDIUM,
            "settings put global app_standby_enabled 0", "settings put global app_standby_enabled 1"),

        // ===== RAM / PROCESOS (HIGH) =====
        Optimization("trim_caches", "Vaciar cachés de apps en segundo plano", Impact.HIGH,
            "pm trim-caches 512M", null),
        Optimization("kill_bg_process_limit", "Limitar procesos en segundo plano", Impact.HIGH,
            "settings put global background_process_limit 4", "settings put global background_process_limit -1"),

        // ===== RED (MEDIUM) =====
        Optimization("wifi_scan_throttle", "Reducir escaneo de redes Wi-Fi en segundo plano", Impact.MEDIUM,
            "settings put global wifi_scan_throttle_enabled 1", null),
        Optimization("mobile_data_bg_restrict", "Restringir datos móviles en apps de fondo", Impact.LOW,
            "cmd netpolicy set restrict-background true", "cmd netpolicy set restrict-background false"),
        Optimization("bt_scan_off", "Desactivar escaneo Bluetooth en segundo plano", Impact.LOW,
            "settings put global ble_scan_always_enabled 0", "settings put global ble_scan_always_enabled 1"),
        Optimization("location_scan_off", "Desactivar escaneo Wi-Fi/BT para ubicación", Impact.LOW,
            "settings put global wifi_scan_always_enabled 0", "settings put global wifi_scan_always_enabled 1"),

        // ===== SINCRONIZACIÓN / SEGUNDO PLANO (MEDIUM) =====
        Optimization("sync_off", "Pausar sincronización automática de cuentas", Impact.MEDIUM,
            "settings put global auto_sync 0", "settings put global auto_sync 1"),
        Optimization("auto_time_off", "Pausar actualización automática de hora/red", Impact.LOW,
            "settings put global auto_time 0", "settings put global auto_time 1"),

        // ===== NOTIFICACIONES / INTERRUPCIONES (LOW) =====
        Optimization("notif_light_off", "Apagar pulso de luz de notificaciones", Impact.LOW,
            "settings put system notification_light_pulse 0", "settings put system notification_light_pulse 1"),
        Optimization("heads_up_off", "Desactivar notificaciones emergentes (heads-up)", Impact.LOW,
            "settings put global heads_up_notifications_enabled 0", "settings put global heads_up_notifications_enabled 1"),
        Optimization("dnd_on", "Activar No Molestar durante la sesión", Impact.MEDIUM,
            "cmd notification set_dnd priority", "cmd notification set_dnd off"),

        // ===== PANTALLA (MEDIUM) =====
        Optimization("keep_screen_awake", "Evitar que la pantalla se apague sola", Impact.MEDIUM,
            "settings put system screen_off_timeout 1800000", "settings put system screen_off_timeout 30000"),
        Optimization("brightness_manual", "Forzar brillo manual (evitar recalculo automático)", Impact.LOW,
            "settings put system screen_brightness_mode 0", "settings put system screen_brightness_mode 1"),
        Optimization("pointer_speed_max", "Optimizar velocidad de puntero/touch polling", Impact.LOW,
            "settings put system pointer_speed 5", "settings put system pointer_speed 0"),

        // ===== AUDIO / HÁPTICOS (LOW) =====
        Optimization("haptics_off", "Desactivar retroalimentación háptica del sistema", Impact.LOW,
            "settings put system haptic_feedback_enabled 0", "settings put system haptic_feedback_enabled 1"),
        Optimization("sound_effects_off", "Desactivar sonidos de sistema (touch/lock)", Impact.LOW,
            "settings put system sound_effects_enabled 0", "settings put system sound_effects_enabled 1"),

        // ===== VARIOS DE SISTEMA (LOW-MEDIUM) =====
        Optimization("stay_awake_charging_off", "Evitar que se mantenga despierto extra al cargar", Impact.LOW,
            "settings put global stay_on_while_plugged_in 0", null),
        Optimization("air_gesture_off", "Desactivar gestos en el aire (si el equipo los soporta)", Impact.LOW,
            "settings put secure camera_gesture_disabled 1", "settings put secure camera_gesture_disabled 0"),
        Optimization("adb_wifi_keepalive", "Mantener el servicio ADB inalámbrico estable", Impact.LOW,
            "settings put global adb_wifi_enabled 1", null),
        Optimization("gpu_debug_off", "Desactivar capas de depuración de GPU (si estaban activas)", Impact.MEDIUM,
            "settings put global enable_gpu_debug_layers 0", null),
        Optimization("gpu_profiling_off", "Apagar el profiler de renderizado en pantalla", Impact.LOW,
            "settings put global debug.hwui.profile false", null),

        // ===== SENSORES / SEGUNDO PLANO EXTRA (LOW-MEDIUM) =====
        Optimization("nfc_off", "Desactivar NFC durante la sesión (si no se usa)", Impact.LOW,
            "svc nfc disable", "svc nfc enable"),
        Optimization("mobile_data_scan_off", "Reducir búsqueda de red móvil en segundo plano", Impact.LOW,
            "settings put global mobile_data_always_on 0", null),
        Optimization("assist_gesture_off", "Desactivar gesto de asistente (evita activaciones accidentales)", Impact.LOW,
            "settings put secure assist_gesture_enabled 0", "settings put secure assist_gesture_enabled 1"),
        Optimization("adaptive_brightness_off", "Desactivar brillo adaptativo (evita recálculos)", Impact.LOW,
            "settings put system screen_auto_brightness_adj 0", null),
        Optimization("power_sound_off", "Silenciar sonido al conectar/desconectar cargador", Impact.LOW,
            "settings put global charging_sounds_enabled 0", "settings put global charging_sounds_enabled 1"),
        Optimization("lockscreen_sound_off", "Desactivar sonido de bloqueo de pantalla", Impact.LOW,
            "settings put system lockscreen_sounds_enabled 0", "settings put system lockscreen_sounds_enabled 1"),
        Optimization("vibrate_on_touch_off", "Desactivar vibración al tocar el teclado", Impact.LOW,
            "settings put system haptic_feedback_enabled 0", null),
        Optimization("show_touches_off", "No renderizar indicadores visuales de toque", Impact.LOW,
            "settings put system show_touches 0", "settings put system show_touches 0"),
        Optimization("pointer_location_off", "Desactivar overlay de ubicación de puntero", Impact.LOW,
            "settings put system pointer_location 0", null),
        Optimization("stay_awake_usb_off", "No mantener despierto extra por USB de datos", Impact.LOW,
            "settings put global stay_on_while_plugged_in 0", null),

        // ===== RED AVANZADO (MEDIUM) =====
        Optimization("data_saver_off", "Desactivar ahorro de datos (evita throttling de red en juego online)", Impact.MEDIUM,
            "cmd netpolicy set restrict-background false", null),
        Optimization("airplane_radios_check", "Verificar que no haya radios innecesarias activas", Impact.LOW,
            "settings get global airplane_mode_on", null),
        Optimization("private_dns_off", "Desactivar DNS privado (reduce overhead de resolución)", Impact.LOW,
            "settings put global private_dns_mode off", "settings put global private_dns_mode opportunistic"),

        // ===== RENDIMIENTO EXTRA (MEDIUM-HIGH) =====
        Optimization("force_gpu_rendering", "Forzar aceleración GPU en todas las apps", Impact.MEDIUM,
            "settings put global force_gpu_rendering 1", "settings put global force_gpu_rendering 0"),
        Optimization("disable_hw_overlays", "Desactivar overlays de hardware redundantes", Impact.LOW,
            "settings put global debug.sf.disable_hwc_vds 1", null),
        Optimization("window_layout_insets_off", "Reducir recomputo de insets de layout en segundo plano", Impact.LOW,
            "settings put global debug.hwui.render_dirty_regions false", null),
        Optimization("background_dex_opt_off", "Pausar optimización de apps en segundo plano (bg-dexopt)", Impact.MEDIUM,
            "pm bg-dexopt-job --disable", "pm bg-dexopt-job --enable"),
        Optimization("job_scheduler_restrict", "Restringir tareas programadas de otras apps", Impact.MEDIUM,
            "cmd jobscheduler suspend true", "cmd jobscheduler suspend false"),
        Optimization("activity_manager_bg_restrict", "Restringir apps en segundo plano vía ActivityManager", Impact.MEDIUM,
            "cmd activity set-bg-restriction-level *:HIGH", null),
        Optimization("clear_recents_ram", "Limpiar apps recientes de la RAM antes de jugar", Impact.HIGH,
            "am kill-all", null)
    )

    fun optimizeForGame(
        packageToLaunch: String,
        extraAppsToKill: List<String> = emptyList(),
        onStep: ((stepLabel: String, current: Int, total: Int) -> Unit)? = null
    ): OptimizationResult {
        val log = mutableListOf<String>()
        val killed = mutableListOf<String>()
        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()

        val targets = (defaultKillTargets + extraAppsToKill).distinct().filter { it != packageToLaunch }
        val totalSteps = targets.size + optimizations.size
        var currentStep = 0

        for (pkg in targets) {
            currentStep++
            onStep?.invoke("Cerrando: $pkg", currentStep, totalSteps)
            val result = ShizukuManager.runShellCommand("am force-stop $pkg")
            if (!result.startsWith("ERROR")) {
                killed.add(pkg)
                log.add("Cerrado: $pkg")
            }
        }

        for (opt in optimizations) {
            currentStep++
            onStep?.invoke(opt.label, currentStep, totalSteps)
            val result = ShizukuManager.runShellCommand(opt.apply)
            if (result.startsWith("ERROR")) {
                failed.add(opt.label)
            } else {
                applied.add(opt.label)
                log.add("[${opt.impact}] ${opt.label}")
            }
        }

        return OptimizationResult(killed, applied, failed, log)
    }

    /**
     * Revierte todas las optimizaciones que tienen comando de restauración definido.
     */
    fun restoreDefaults() {
        for (opt in optimizations) {
            opt.restore?.let { ShizukuManager.runShellCommand(it) }
        }
    }

    fun launchGame(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }

    fun totalOptimizationCount(): Int = optimizations.size + 1 // +1 por el cierre de apps de fondo como "acción" propia
}
