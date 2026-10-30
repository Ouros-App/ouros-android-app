package com.ouros.app.feature.auth.choose

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.ouros.app.R
import com.ouros.app.core.util.applyBottomSystemBarPadding
import com.ouros.app.feature.auth.firstaccess.FirstAccessActivity
import com.ouros.app.feature.auth.login.LoginActivity

class ChooseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_choose)

        findViewById<View>(R.id.materialCardView).applyBottomSystemBarPadding()

        findViewById<View>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this,LoginActivity::class.java))
        }

        findViewById<View>(R.id.btnFirstAccess).setOnClickListener {
            startActivity(Intent(this, FirstAccessActivity::class.java))
        }
    }
}
