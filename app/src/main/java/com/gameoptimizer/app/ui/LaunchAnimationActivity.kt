package com.gameoptimizer.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gameoptimizer.app.R
import com.gameoptimizer.app.optimizer.GameOptimizer
import com.gameoptimizer.app.overlay.FpsOverlayService

/**
 * Pantalla de animación de "inicio de juego": se muestra al presionar
 * un juego en la lista, corre las optimizaciones en background mostrando
 * progreso animado, y al terminar lanza el juego + overlay de FPS.
 */
class LaunchAnimationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_GAME_LABEL = "game_label"
    }

    private lateinit var gameOptimizer: GameOptimizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch_animation)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run { finish(); return }
        val gameLabel = intent.getStringExtra(EXTRA_GAME_LABEL) ?: "Juego"

        gameOptimizer = GameOptimizer(this)

        val ivRing = findViewById<ImageView>(R.id.ivRing)
        val tvLogo = findViewById<TextView>(R.id.tvLogo)
        val tvGameName = findViewById<TextView>(R.id.tvGameName)
        val tvCurrentStep = findViewById<TextView>(R.id.tvCurrentStep)
        val tvCounter = findViewById<TextView>(R.id.tvCounter)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        tvGameName.text = gameLabel

        // Animaciones: anillo girando sin parar, logo pulsando
        ivRing.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_ring))
        tvLogo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_logo))

        Thread {
            gameOptimizer.optimizeForGame(packageName) { stepLabel, current, total ->
                runOnUiThread {
                    tvCurrentStep.text = stepLabel
                    tvCounter.text = "$current / $total optimizaciones"
                    progressBar.progress = (current * 100) / total
                }
            }

            runOnUiThread {
                tvCurrentStep.text = "¡Listo! Lanzando juego..."
                progressBar.progress = 100
            }

            Thread.sleep(400) // pequeño respiro para que se vea el "100%" antes de saltar al juego

            runOnUiThread {
                gameOptimizer.launchGame(packageName)
                startFpsOverlay(packageName)
                finish()
            }
        }.start()
    }

    private fun startFpsOverlay(packageName: String) {
        val intent = Intent(this, FpsOverlayService::class.java).apply {
            putExtra(FpsOverlayService.EXTRA_TARGET_PACKAGE, packageName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onBackPressed() {
        // Evitamos que cancele la optimización a medias con un back accidental
    }
}
