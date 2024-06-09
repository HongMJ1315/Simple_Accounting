package com.ntou.simpleaccounting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ntou.simpleaccounting.ui.theme.SimpleAccountingTheme
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.firestore.FieldValue
import androidx.compose.foundation.lazy.items

class HomeActivity : ComponentActivity() {
    data class Record(val type: String, val amount: String, val description: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimpleAccountingTheme {
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
        FirebaseAuth.getInstance().signOut()
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("is_logged_in", false).apply()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    @Composable
    fun HomeScreen() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val email = user?.email ?: "No email available"

        var userID by remember { mutableStateOf<String?>(null) }
        val firestore = FirebaseFirestore.getInstance()

        var amount by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var recordType by remember { mutableStateOf("Income") }
        var records by remember { mutableStateOf(listOf<Record>()) }
        var showDialog by remember { mutableStateOf(false) }
        var monthlyIncome by remember { mutableStateOf(0) }
        var monthlyExpense by remember { mutableStateOf(0) }
        var netIncome by remember { mutableStateOf(0) }

        // State for the current date
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var currentDate by remember { mutableStateOf(dateFormat.format(Date())) }

        LaunchedEffect(user) {
            user?.uid?.let { uid ->
                firestore.collection("users").document(uid).get()
                    .addOnSuccessListener { document ->
                        if (document != null) {
                            userID = document.getString("userID")
                            loadRecords(uid, currentDate, firestore) { loadedRecords ->
                                records = loadedRecords
                            }
                        }
                    }
                    .addOnFailureListener {
                        userID = "Failed to fetch userID"
                    }
            }
        }

        fun addRecord() {
            if (amount.isNotEmpty() && description.isNotEmpty()) {
                val newRecord = Record(recordType, amount, description)
                records = records + newRecord
                saveRecord(user?.uid, currentDate, firestore, newRecord)
                amount = ""
                description = ""
            }
        }

        fun changeDate(offset: Int, unit: Int = Calendar.DAY_OF_YEAR) {
            val calendar = Calendar.getInstance().apply {
                time = dateFormat.parse(currentDate) ?: Date()
                add(unit, offset)
            }
            currentDate = dateFormat.format(calendar.time)
            user?.uid?.let { uid ->
                loadRecords(uid, currentDate, firestore) { loadedRecords ->
                    records = loadedRecords
                }
            }
        }

        fun deleteRecord(record: Record) {
            user?.uid?.let { uid ->
                val updatedRecords = records - record
                records = updatedRecords
                deleteRecordFromFirestore(uid, currentDate, firestore, record)
            }
        }

        fun calculateMonthlyTotals(uid: String, date: String) {
            val calendar = Calendar.getInstance().apply {
                time = dateFormat.parse(date) ?: Date()
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val startDate = dateFormat.format(calendar.time)
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val endDate = dateFormat.format(calendar.time)

            loadMonthlyRecords(uid, startDate, endDate, firestore) { monthlyRecords ->
                var income = 0
                var expense = 0
                for (record in monthlyRecords) {
                    val amount = record.amount.toIntOrNull() ?: 0
                    if (record.type == "Income") {
                        income += amount
                    } else if (record.type == "Expense") {
                        expense += amount
                    }
                }
                monthlyIncome = income
                monthlyExpense = expense
                netIncome = income - expense
                showDialog = true
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            item {
                Text("Welcome to the Home Screen!", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Logged in as: $email", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("User ID: ${userID ?: "Loading..."}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Date: $currentDate", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { changeDate(-1, Calendar.MONTH) }) {
                        Text("Previous Month")
                    }
                    Button(onClick = { changeDate(-1) }) {
                        Text("Previous Day")
                    }
                    Button(onClick = { changeDate(1) }) {
                        Text("Next Day")
                    }
                    Button(onClick = { changeDate(1, Calendar.MONTH) }) {
                        Text("Next Month")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) {
                            amount = it
                        }
                    },
                    label = { Text("輸入金額") },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("事由") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { recordType = "Income" }) {
                        Text("收入")
                    }
                    Button(onClick = { recordType = "Expense" }) {
                        Text("支出")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { addRecord() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Record")
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { user?.uid?.let { uid -> calculateMonthlyTotals(uid, currentDate) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查看支出收入")
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text("Records:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(records) { record ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${record.type}: $${record.amount} - ${record.description}", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { deleteRecord(record) }
                        ) {
                            Text("Delete")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                val totalIncome = records.filter { it.type == "Income" }.sumBy { it.amount.toInt() }
                val totalExpense = records.filter { it.type == "Expense" }.sumBy { it.amount.toInt() }
                val totalProfit = totalIncome - totalExpense

                // 顯示總盈餘
                Text("總盈餘: $totalProfit", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { logout() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            }
        }

        if (showDialog) {
            ShowMonthlySummaryDialog(
                monthlyIncome = monthlyIncome,
                monthlyExpense = monthlyExpense,
                netIncome = netIncome,
                onDismiss = { showDialog = false }
            )
        }
    }

    @Composable
    fun ShowMonthlySummaryDialog(monthlyIncome: Int, monthlyExpense: Int, netIncome: Int, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("月收入支出總覽") },
            text = {
                Column {
                    Text("總收入: $$monthlyIncome")
                    Text("總支出: $$monthlyExpense")
                    Text("淨收入: $$netIncome")
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("確定")
                }
            }
        )
    }

    private fun loadRecords(uid: String, date: String, firestore: FirebaseFirestore, onRecordsLoaded: (List<Record>) -> Unit) {
        firestore.collection("users").document(uid).collection("records").document(date).get()
            .addOnSuccessListener { document ->
                val loadedRecords = document["records"] as? List<*>
                val convertedRecords = loadedRecords?.mapNotNull { record ->
                    if (record is Map<*, *>) {
                        val type = record["type"] as? String ?: ""
                        val amount = record["amount"] as? String ?: ""
                        val description = record["description"] as? String ?: ""
                        Record(type, amount, description)
                    } else {
                        null
                    }
                } ?: emptyList()
                onRecordsLoaded(convertedRecords)
            }
            .addOnFailureListener {
                onRecordsLoaded(emptyList())
            }
    }

    private fun loadMonthlyRecords(uid: String, startDate: String, endDate: String, firestore: FirebaseFirestore, onRecordsLoaded: (List<Record>) -> Unit) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startCalendar = Calendar.getInstance().apply {
            time = dateFormat.parse(startDate) ?: Date()
        }
        val endCalendar = Calendar.getInstance().apply {
            time = dateFormat.parse(endDate) ?: Date()
        }
        val recordsList = mutableListOf<Record>()
        val dates = generateSequence(startCalendar) { calendar ->
            calendar.takeIf { it.before(endCalendar) }?.apply { add(Calendar.DAY_OF_MONTH, 1) }
        }.map { dateFormat.format(it.time) }.toList()

        var remainingDates = dates.size

        dates.forEach { date ->
            firestore.collection("users").document(uid).collection("records").document(date).get()
                .addOnSuccessListener { document ->
                    val loadedRecords = document["records"] as? List<*>
                    val convertedRecords = loadedRecords?.mapNotNull { record ->
                        if (record is Map<*, *>) {
                            val type = record["type"] as? String ?: ""
                            val amount = record["amount"] as? String ?: ""
                            val description = record["description"] as? String ?: ""
                            Record(type, amount, description)
                        } else {
                            null
                        }
                    } ?: emptyList()
                    recordsList.addAll(convertedRecords)
                    if (--remainingDates == 0) {
                        onRecordsLoaded(recordsList)
                    }
                }
                .addOnFailureListener {
                    if (--remainingDates == 0) {
                        onRecordsLoaded(recordsList)
                    }
                }
        }
    }

    private fun saveRecord(uid: String?, date: String, firestore: FirebaseFirestore, record: Record) {
        uid?.let {
            val recordData = hashMapOf(
                "type" to record.type,
                "amount" to record.amount,
                "description" to record.description
            )
            val docRef = firestore.collection("users").document(it).collection("records").document(date)
            docRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    docRef.update("records", FieldValue.arrayUnion(recordData))
                } else {
                    docRef.set(hashMapOf("records" to listOf(recordData)))
                }
            }
        }
    }

    private fun deleteRecordFromFirestore(uid: String, date: String, firestore: FirebaseFirestore, record: Record) {
        val recordData = hashMapOf(
            "type" to record.type,
            "amount" to record.amount,
            "description" to record.description
        )
        val docRef = firestore.collection("users").document(uid).collection("records").document(date)
        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                docRef.update("records", FieldValue.arrayRemove(recordData))
            }
        }
    }
}
