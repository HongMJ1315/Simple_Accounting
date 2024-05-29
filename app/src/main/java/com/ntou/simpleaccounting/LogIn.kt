package com.ntou.simpleaccounting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ntou.simpleaccounting.ui.theme.SimpleAccountingTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 檢查登入狀態
        if (isLoggedIn(this)) {
            // 如果已登入，跳轉到主活動
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish() // 關閉 LoginActivity 以防止返回
        } else {
            // 如果未登入，顯示登入畫面
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
        finish() // 關閉 LoginActivity 以防止返回
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

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (isLoginMode) "Login" else "Register", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        BasicTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (email.isEmpty()) {
                        Text("Email", style = MaterialTheme.typography.bodyMedium)
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        BasicTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (password.isEmpty()) {
                        Text("Password", style = MaterialTheme.typography.bodyMedium)
                    }
                    innerTextField()
                }
            }
        )
        if (!isLoginMode) {
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                decorationBox = { innerTextField ->
                    Box(
                        Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (confirmPassword.isEmpty()) {
                            Text("Confirm Password", style = MaterialTheme.typography.bodyMedium)
                        }
                        innerTextField()
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (isLoginMode) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                message = "Login successful!"
                                onLoginSuccess()
                            } else {
                                message = "Login failed: ${task.exception?.message}"
                            }
                        }
                } else {
                    if (password == confirmPassword) {
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    message = "Registration successful!"
                                    onLoginSuccess()
                                } else {
                                    message = "Registration failed: ${task.exception?.message}"
                                }
                            }
                    } else {
                        message = "Passwords do not match"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoginMode) "Login" else "Register")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { isLoginMode = !isLoginMode }) {
            Text(if (isLoginMode) "Switch to Register" else "Switch to Login")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

fun saveLoginState(context: Context, isLoggedIn: Boolean) {
    val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    sharedPreferences.edit().putBoolean("is_logged_in", isLoggedIn).apply()
}

fun isLoggedIn(context: Context): Boolean {
    val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return sharedPreferences.getBoolean("is_logged_in", false)
}

//@Preview(showBackground = true)
//@Composable
//fun LoginScreenPreview() {
//    SimpleAccountingTheme {
//        LoginScreen()
//    }
//}
