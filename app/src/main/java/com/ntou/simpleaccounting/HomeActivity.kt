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
import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

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

    @Composable
    fun HomeScreen() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val email = user?.email ?: stringResource(id = R.string.no_email)

        var userID by remember { mutableStateOf<String?>(null) }
        val firestore = FirebaseFirestore.getInstance()

        var amount by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }

        val recordTypeStrings = listOf(
            stringResource(id = R.string.income),
            stringResource(id = R.string.expense)
        )
        var recordTypeIndex by remember { mutableIntStateOf(0) }
        val recordType = recordTypeStrings[recordTypeIndex]

        var records by remember { mutableStateOf(listOf<Record>()) }
        var showDialog by remember { mutableStateOf(false) }
        var monthlyIncome by remember { mutableIntStateOf(0) }
        var monthlyExpense by remember { mutableIntStateOf(0) }
        var netIncome by remember { mutableIntStateOf(0) }

        var showChart by remember { mutableStateOf(false) }

        // State for storing daily income and expense data
        var dailyIncomeData by remember { mutableStateOf(listOf<Float>()) }
        var dailyExpenseData by remember { mutableStateOf(listOf<Float>()) }

        // State for the current date
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var currentDate by remember { mutableStateOf(dateFormat.format(Date())) }

        val context = LocalContext.current
        val calendar = Calendar.getInstance()

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

        // Function to calculate daily totals for the chart
        fun calculateDailyTotalsForChart(uid: String, date: String) {
            val calendar = Calendar.getInstance().apply {
                time = dateFormat.parse(date) ?: Date()
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val startDate = dateFormat.format(calendar.time)
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val endDate = dateFormat.format(calendar.time)

            loadDailyRecords(uid, startDate, endDate, firestore) { dailyRecords ->
                val incomeList = MutableList(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) { 0f }
                val expenseList = MutableList(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) { 0f }
                for (record in dailyRecords) {
                    val amount = record.amount.toFloatOrNull() ?: 0f
                    val day = SimpleDateFormat("d", Locale.getDefault()).format(dateFormat.parse(record.date)).toInt() - 1
                    if (record.type == "Income") {
                        incomeList[day] += amount
                    } else if (record.type == "Expense") {
                        expenseList[day] += amount
                    }
                }
                dailyIncomeData = incomeList
                dailyExpenseData = expenseList
                showChart = true
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

        fun changeDate(year: Int, month: Int, day: Int) {
            calendar.set(year, month, day)
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

        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                changeDate(year, month, dayOfMonth)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            item {
                Text(stringResource(id = R.string.welcome_message), style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(id = R.string.logged_in_as, email), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("${stringResource(id = R.string.user_id)}: ${userID ?: "Loading..."}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Text("${stringResource(id = R.string.date)}: $currentDate", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Row {
                    Button(onClick = { changeDate( -1) }) {
                        Text(text = stringResource(id = R.string.previous_day))
                    }
                    // Replace the date change buttons with a button to show the date picker
                    Button(onClick = { datePickerDialog.show() }) {
                        Text(text = stringResource(id = R.string.select_date))
                    }
                    Button(onClick = { changeDate( 1) }) {
                        Text(text = stringResource(id = R.string.next_day))
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
                    label = { Text(stringResource(id = R.string.enter_amount)) },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(id = R.string.description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { recordTypeIndex = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (recordTypeIndex == 0) Color.Blue else Color.Gray
                        )
                    ) {
                        Text(stringResource(id = R.string.income))
                    }
                    Button(
                        onClick = { recordTypeIndex = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor  = if (recordTypeIndex == 1) Color.Blue else Color.Gray
                        )
                    ) {
                        Text(stringResource(id = R.string.expense))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { addRecord() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.add_record))
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { user?.uid?.let { uid -> calculateMonthlyTotals(uid, currentDate) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.view_income_expense))
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(id = R.string.records), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(id = R.string.type), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(id = R.string.amount), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(id = R.string.description), modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(id = R.string.delete), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))

            }

            items(records) { record ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(record.type, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(record.amount, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(record.description, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { deleteRecord(record) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.delete))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                val totalIncome = records.filter { it.type == "Income" }.sumOf { it.amount.toInt() }
                val totalExpense = records.filter { it.type == "Expense" }.sumOf { it.amount.toInt() }
                val totalProfit = totalIncome - totalExpense

                // Display total profit
                Text(stringResource(id = R.string.total_profit, totalProfit), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { user?.uid?.let { uid -> calculateDailyTotalsForChart(uid, currentDate) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.visualize))
                }
                Spacer(modifier = Modifier.height(16.dp))
                // When "Visualize Income/Expense" is clicked, call the function to calculate monthly totals and display the chart
                if (showChart) {
                    LineChart(dailyIncomeData, dailyExpenseData)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { logout() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.logout))
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
    fun ShowMonthlySummaryDialog(
        monthlyIncome: Int,
        monthlyExpense: Int,
        netIncome: Int,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(id = R.string.monthly_summary)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.total_income, monthlyIncome) + "$")
                    Text(stringResource(id = R.string.total_expense, monthlyExpense) + "$")
                    Text(stringResource(id = R.string.net_income, netIncome) + "$")
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text(stringResource(id = R.string.confirm))
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

    private fun loadDailyRecords(uid: String, startDate: String, endDate: String, firestore: FirebaseFirestore, onRecordsLoaded: (List<RecordWithDate>) -> Unit) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startCalendar = Calendar.getInstance().apply {
            time = dateFormat.parse(startDate) ?: Date()
        }
        val endCalendar = Calendar.getInstance().apply {
            time = dateFormat.parse(endDate) ?: Date()
        }
        val recordsList = mutableListOf<RecordWithDate>()
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
                            RecordWithDate(type, amount, description, date)
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

    data class RecordWithDate(val type: String, val amount: String, val description: String, val date: String)

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

    @Composable
    fun LineChart(incomeData: List<Float>, expenseData: List<Float>) {
        val maxDaysInMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.White)
        ) {
            val maxValue = (incomeData + expenseData).maxOrNull() ?: 0f
            val stepX = size.width / maxDaysInMonth
            val stepY = size.height / maxValue

            val incomePath = Path().apply {
                moveTo(0f, size.height)
                incomeData.forEachIndexed { index, value ->
                    lineTo(index * stepX, size.height - (value * stepY))
                }
            }

            val expensePath = Path().apply {
                moveTo(0f, size.height)
                expenseData.forEachIndexed { index, value ->
                    lineTo(index * stepX, size.height - (value * stepY))
                }
            }

            drawPath(incomePath, Color.Red, style = Stroke(width = 2f))
            drawPath(expensePath, Color.Green, style = Stroke(width = 2f))

            // Draw date labels
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 20f
                textAlign = android.graphics.Paint.Align.CENTER
            }

            for (day in 1..maxDaysInMonth) {
                val x = (day - 1) * stepX
                drawContext.canvas.nativeCanvas.drawText(
                    day.toString(),
                    x,
                    size.height - 5f, // Adjust the Y position as needed
                    textPaint
                )
            }

            // Draw amount labels
            val yStep = maxValue / 5  // Divide Y-axis into 5 steps
            val yLabelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 20f
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            for (i in 0..5) {
                val y = size.height - (i * stepY * yStep)
                drawContext.canvas.nativeCanvas.drawText(
                    (i * yStep).toInt().toString(),
                    50f, // Adjust the X position as needed
                    y,
                    yLabelPaint
                )
            }
        }
    }

}
