package com.ntou.simpleaccounting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntou.simpleaccounting.ui.theme.SimpleAccountingTheme

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
    val firestore = FirebaseFirestore.getInstance()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var userID by remember { mutableStateOf("") }
    var messageIndex by remember { mutableIntStateOf(0)}
    val messageStrings = listOf(
        stringResource(id = R.string.login_successful),
        stringResource(id = R.string.login_failed),
        stringResource(id = R.string.registration_successful),
        stringResource(id = R.string.save_user_data_failed),
        stringResource(id = R.string.registration_failed),
        stringResource(id = R.string.user_id_exists),
        stringResource(id = R.string.error_checking_user_id),
        stringResource(id = R.string.passwords_do_not_match),
    )
    var message = messageStrings[messageIndex]
    var isLoginMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (isLoginMode) stringResource(id = R.string.login) else stringResource(id = R.string.register), style = MaterialTheme.typography.headlineMedium)
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
                        Text(stringResource(id = R.string.email), style = MaterialTheme.typography.bodyMedium)
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
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (password.isEmpty()) {
                        Text(stringResource(id = R.string.password), style = MaterialTheme.typography.bodyMedium)
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
                            Text(stringResource(id = R.string.password), style = MaterialTheme.typography.bodyMedium)
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = userID,
                onValueChange = { userID = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                decorationBox = { innerTextField ->
                    Box(
                        Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (userID.isEmpty()) {
                            Text(stringResource(id = R.string.user_id), style = MaterialTheme.typography.bodyMedium)
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
                                messageIndex = 0
                                onLoginSuccess()
                            } else {
                                messageIndex = 1
                                message += ": ${task.exception?.message}"
                            }
                        }
                } else {
                    if (password == confirmPassword) {
                        // Check if userID is unique
                        firestore.collection("users").whereEqualTo("userID", userID).get()
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    if (task.result.isEmpty) {
                                        // Register new user
                                        auth.createUserWithEmailAndPassword(email, password)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    // Save userID in Firestore
                                                    val user = auth.currentUser
                                                    val userData = hashMapOf(
                                                        "userID" to userID,
                                                        "email" to email
                                                    )
                                                    firestore.collection("users").document(user!!.uid).set(userData)
                                                        .addOnCompleteListener { userTask ->
                                                            if (userTask.isSuccessful) {
                                                                messageIndex = 2
                                                                onLoginSuccess()
                                                            } else {
                                                                messageIndex = 3
                                                                message += ": ${userTask.exception?.message}"
                                                            }
                                                        }
                                                } else {
                                                    messageIndex = 4
                                                    message += ": ${task.exception?.message}"
                                                }
                                            }
                                    } else {
                                        messageIndex = 5
                                    }
                                } else {
                                    messageIndex = 6
                                    message += ": ${task.exception?.message}"
                                }
                            }
                    } else {
                        messageIndex = 7
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(if (isLoginMode) stringResource(id = R.string.login) else stringResource(id = R.string.register))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { isLoginMode = !isLoginMode }) {
            Text(if (isLoginMode)  stringResource(id = R.string.switch_to_register) else  stringResource(id = R.string.switch_to_login))
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
