package com.gameoptimizer.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.gameoptimizer.app.databinding.ActivityMainBinding
import com.gameoptimizer.app.optimizer.GameDetector
import com.gameoptimizer.app.optimizer.GameOptimizer
import com.gameoptimizer.app.optimizer.InstalledGame
import com.gameoptimizer.app.overlay.FpsOverlayService
import com.gameoptimizer.app.shizuku.ShizukuManager
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gameOptimizer: GameOptimizer
    private var targetGamePackage: String = ""

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            refreshUiState()
        } else {
            Toast.makeText(this, "Shizuku: permiso denegado", Toast.LENGTH_SHORT).show()
            refreshUiState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameOptimizer = GameOptimizer(this)

        binding.btnInstallShizuku.setOnClickListener {
            ShizukuManager.openShizukuInPlayStore(this)
        }

        binding.btnOpenDevOptions.setOnClickListener {
            ShizukuManager.openDeveloperOptions(this)
        }

        binding.btnGrantPermission.setOnClickListener {
            ShizukuManager.requestPermission(permissionListener)
        }

        binding.rvGames.layoutManager = LinearLayoutManager(this)

        binding.btnRestore.setOnClickListener {
            gameOptimizer.restoreDefaults()
            Toast.makeText(this, "Ajustes del sistema restaurados", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.removeListener(permissionListener)
    }

    private fun refreshUiState() {
        val installed = ShizukuManager.isShizukuInstalled(this)
        val running = ShizukuManager.isServiceRunning()
        val hasPermission = ShizukuManager.hasPermission()

        binding.btnInstallShizuku.isEnabled = !installed
        binding.btnOpenDevOptions.isEnabled = installed && !running
        binding.btnGrantPermission.isEnabled = installed && running && !hasPermission
        binding.rvGames.isEnabled = hasPermission
        binding.btnRestore.isEnabled = hasPermission

        binding.tvStatus.text = when {
            !installed -> "Shizuku no está instalado"
            !running -> "Shizuku instalado, pero no está activo. Actívalo con el código de emparejamiento."
            !hasPermission -> "Shizuku activo. Falta otorgar permiso a esta app."
            else -> "Listo. Elige un juego de la lista."
        }

        if (hasPermission) {
            loadGamesList()
        }
    }

    private fun loadGamesList() {
        Thread {
            val games = GameDetector.getInstalledGames(this)
            runOnUiThread {
                if (games.isEmpty()) {
                    Toast.makeText(
                        this,
                        "No se detectaron juegos instalados (algunas apps no se marcan como 'juego')",
                        Toast.LENGTH_LONG
                    ).show()
                }
                binding.rvGames.adapter = GameListAdapter(games) { game ->
                    optimizeAndLaunch(game)
                }
            }
        }.start()
    }

    private fun optimizeAndLaunch(game: InstalledGame) {
        targetGamePackage = game.packageName
        Toast.makeText(this, "Optimizando para ${game.label}...", Toast.LENGTH_SHORT).show()

        Thread {
            val result = gameOptimizer.optimizeForGame(targetGamePackage)

            runOnUiThread {
                Toast.makeText(
                    this,
                    "Optimización aplicada: ${result.killedApps.size} apps cerradas",
                    Toast.LENGTH_SHORT
                ).show()

                gameOptimizer.launchGame(targetGamePackage)
                startFpsOverlay()
            }
        }.start()
    }

    private fun startFpsOverlay() {
        val intent = Intent(this, FpsOverlayService::class.java).apply {
            putExtra(FpsOverlayService.EXTRA_TARGET_PACKAGE, targetGamePackage)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
