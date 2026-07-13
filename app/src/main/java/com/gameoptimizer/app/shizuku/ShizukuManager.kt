package com.gameoptimizer.app.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Encapsula toda la interacción con Shizuku:
 * - Detectar si la app Shizuku está instalada
 * - Detectar si el servicio está corriendo (pareado)
 * - Pedir permiso a la app
 * - Ejecutar comandos shell con privilegios ADB (sin root)
 */
object ShizukuManager {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val PERMISSION_REQUEST_CODE = 1001

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun openShizukuInPlayStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$SHIZUKU_PACKAGE")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openDeveloperOptions(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isServiceRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isServiceRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun removeListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    /**
     * Ejecuta un comando shell usando el servicio de Shizuku (equivalente a "adb shell <cmd>").
     * Devuelve la salida estándar como texto.
     */
    fun runShellCommand(command: String): String {
        if (!hasPermission()) return ""
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString()
        } catch (e: Throwable) {
            "ERROR: ${e.message}"
        }
    }
}
