package com.ouros.app.feature.error

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.ouros.app.R

class ErrorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_error)

        findViewById<TextView>(R.id.txtErrorMessage).text =
            intent.getStringExtra(EXTRA_MESSAGE)
                ?: "Não foi possível concluir a operação."

        findViewById<ImageView>(R.id.btnErrorBack).setOnClickListener {
            finish()
        }
    }

    companion object {
        const val EXTRA_MESSAGE = "error_message"
    }
}