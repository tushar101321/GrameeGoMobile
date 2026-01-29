package com.example.grameego

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        setContentView(R.layout.activity_main)

        val token = SessionManager.getToken(this)
        val role = SessionManager.getRole(this)

        if (token.isNullOrBlank() || role.isNullOrBlank()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        when (role) {
            "customer" -> startActivity(Intent(this, CustomerActivity::class.java))
            "driver" -> startActivity(Intent(this, DriverActivity::class.java))
            "shop" -> startActivity(Intent(this, ShopActivity::class.java))
            else -> {
                SessionManager.clear(this)
                startActivity(Intent(this, AuthActivity::class.java))
            }
        }
        finish()
    }
}
