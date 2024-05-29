package com.ntou.simpleaccounting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ntou.simpleaccounting.ui.theme.SimpleAccountingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isLoggedIn(this)) {
            // If the user is logged in, navigate to HomeActivity
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish() // Close MainActivity to prevent back navigation to the login screen
        } else {
            // If the user is not logged in, show the login screen
            setContent {
                SimpleAccountingTheme {
                    // A surface container using the 'background' color from the theme
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LoginScreen { loginSuccessful() }
                    }
                }
            }
        }
    }

    private fun loginSuccessful() {
        saveLoginState(this, true)
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish() // Close MainActivity to prevent back navigation to the login screen
    }

    private fun saveLoginState(context: Context, isLoggedIn: Boolean) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("is_logged_in", isLoggedIn).apply()
    }

    private fun isLoggedIn(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("is_logged_in", false)
    }
}
