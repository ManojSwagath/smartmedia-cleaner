package com.example.app

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false
    private val navigateRunnable = Runnable { goToMain() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logoText = findViewById<TextView>(R.id.simoLogo)

        // Start from smaller + transparent, then pop-in.
        logoText.scaleX = 0.75f
        logoText.scaleY = 0.75f
        logoText.alpha = 0f

        logoText.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(520)
            .setInterpolator(OvershootInterpolator(1.15f))
            .withEndAction {
                // Small settle pulse to feel "alive" without being scammy.
                logoText.animate()
                    .scaleX(0.98f)
                    .scaleY(0.98f)
                    .setDuration(140)
                    .setInterpolator(OvershootInterpolator(0.9f))
                    .withEndAction {
                        logoText.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(140)
                            .start()
                    }
                    .start()
            }
            .start()

        // Go to home shortly after the animation begins.
        handler.postDelayed(navigateRunnable, 850)

        // Tap anywhere to skip.
        findViewById<View>(R.id.splashRoot).setOnClickListener {
            goToMain()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        super.onDestroy()
    }

    private fun goToMain() {
        if (hasNavigated) return
        hasNavigated = true
        handler.removeCallbacks(navigateRunnable)

        val intent = Intent(this, MainActivity::class.java)
        val options = ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        startActivity(intent, options.toBundle())
        finish()
    }
}
