package com.ouros.app.feature.onboarding.welcome

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.ouros.app.R
import com.ouros.app.feature.onboarding.tutorial.TutorialActivity

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java))
            finish()
        }
    }
}