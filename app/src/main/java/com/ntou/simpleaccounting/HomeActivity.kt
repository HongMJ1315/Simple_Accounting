package com.ntou.simpleaccounting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntou.simpleaccounting.ui.theme.SimpleAccountingTheme

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimpleAccountingTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen()
                }
            }
        }
    }

    private fun logout() {
        // Log out the user
        FirebaseAuth.getInstance().signOut()

        // Clear login state
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("is_logged_in", false).apply()

        // Redirect to LoginActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    @Composable
    fun HomeScreen() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val email = user?.email ?: "No email available"

        // State to hold userID
        var userID by remember { mutableStateOf<String?>(null) }
        val firestore = FirebaseFirestore.getInstance()

        // Fetch userID from Firestore
        LaunchedEffect(user) {
            user?.uid?.let { uid ->
                firestore.collection("users").document(uid).get()
                    .addOnSuccessListener { document ->
                        if (document != null) {
                            userID = document.getString("userID")
                        }
                    }
                    .addOnFailureListener {
                        userID = "Failed to fetch userID"
                    }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text("Welcome to the Home Screen!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Logged in as: $email", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("User ID: ${userID ?: "Loading..."}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { logout() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
            }
        }
    }
}
