package nkdevelopment.net.sire_questions

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.inspectionapp.ui.theme.InspectionAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sectionId = intent.getIntExtra("SECTION_ID", 1)

        setContent {
            InspectionAppTheme {
                SectionScreen(
                    sectionId = sectionId,
                    onBackClicked = { finish() },
                    context = this
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(sectionId: Int, onBackClicked: () -> Unit, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val sectionJsonFileName = "section_${sectionId}.json"

    // Questionnaire state
    val questions = remember { mutableStateListOf<Question>() }
    val answers = remember { mutableStateMapOf<String, String>() }

    // Guide dialog state
    var showGuideDialog by remember { mutableStateOf(false) }
    var currentGuideText by remember { mutableStateOf("") }

    // Show all unanswered questions state
    var showUnansweredWarning by remember { mutableStateOf(false) }

    // Load questions from JSON file
    LaunchedEffect(sectionId) {
        try {
            val jsonString = context.assets.open(sectionJsonFileName).bufferedReader().use { it.readText() }
            val type = object : TypeToken<QuestionnaireData>() {}.type
            val data = Gson().fromJson<QuestionnaireData>(jsonString, type)
            questions.clear()
            questions.addAll(data.questions)
        } catch (e: Exception) {
            Log.e("SectionActivity", "Error loading JSON: ${e.message}")
            // If section_1.json file exists in assets, use it for testing purposes
            try {
                val jsonString = context.assets.open("section_1.json").bufferedReader().use { it.readText() }
                val type = object : TypeToken<QuestionnaireData>() {}.type
                val data = Gson().fromJson<QuestionnaireData>(jsonString, type)
                questions.clear()
                questions.addAll(data.questions)
            } catch (e: Exception) {
                Log.e("SectionActivity", "Error loading fallback JSON: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Section $sectionId Questions", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = sections.find { it.id == sectionId }?.title ?: "Section $sectionId",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (questions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(questions) { question ->
                            QuestionItem(
                                question = question,
                                answer = answers[question.question_number] ?: "",
                                onAnswerChanged = { newAnswer ->
                                    answers[question.question_number] = newAnswer
                                },
                                onGuideClicked = {
                                    currentGuideText = question.guide_to_inspection
                                    showGuideDialog = true
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val unansweredQuestions = questions.filter {
                                answers[it.question_number].isNullOrBlank() &&
                                        it.possible_answers?.contains("N/A") != true
                            }

                            if (unansweredQuestions.isEmpty()) {
                                coroutineScope.launch {
                                    exportToPdf(context, sectionId, questions, answers)
                                }
                            } else {
                                showUnansweredWarning = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Export as PDF", fontSize = 16.sp)
                    }
                }
            }

            // Guide Dialog
            if (showGuideDialog) {
                AlertDialog(
                    onDismissRequest = { showGuideDialog = false },
                    title = { Text("Guide to Inspection") },
                    text = {
                        Text(
                            text = currentGuideText.ifEmpty { "No guide information available." },
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showGuideDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // Unanswered Questions Warning
            if (showUnansweredWarning) {
                AlertDialog(
                    onDismissRequest = { showUnansweredWarning = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Unanswered Questions")
                        }
                    },
                    text = {
                        Text("There are unanswered questions. Please complete all questions before exporting to PDF.")
                    },
                    confirmButton = {
                        TextButton(onClick = { showUnansweredWarning = false }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun QuestionItem(
    question: Question,
    answer: String,
    onAnswerChanged: (String) -> Unit,
    onGuideClicked: () -> Unit
) {
    val isAnswered = answer.isNotBlank()
    val possibleAnswers = question.possible_answers

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = if (isAnswered) Color.Transparent else MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Question header with number and text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question.question_number,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )

                Text(
                    text = question.question_text,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )

                if (isAnswered) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Answered",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Answer options
            if (possibleAnswers != null && possibleAnswers.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    possibleAnswers.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAnswerChanged(option) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = answer == option,
                                onClick = { onAnswerChanged(option) }
                            )
                            Text(
                                text = option,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            } else {
                // Text input for open-ended questions
                OutlinedTextField(
                    value = answer,
                    onValueChange = onAnswerChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter your answer") },
                    singleLine = false,
                    maxLines = 4
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guide to inspection button
            TextButton(
                onClick = onGuideClicked,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Guide to Inspection",
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("Guide to Inspection")
            }
        }
    }
}

suspend fun exportToPdf(context: Context, sectionId: Int, questions: List<Question>, answers: Map<String, String>) {
    withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint()
            paint.color = android.graphics.Color.BLACK
            paint.textSize = 12f

            // Title
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("Section $sectionId: ${sections.find { it.id == sectionId }?.title}", 50f, 50f, paint)

            // Date
            paint.textSize = 12f
            paint.isFakeBoldText = false
            val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
            canvas.drawText("Date: ${dateFormat.format(Date())}", 50f, 80f, paint)

            // Questions and Answers
            var yPosition = 120f
            questions.forEach { question ->
                if (yPosition > 750f) {
                    // Create a new page if we're running out of space
                    document.finishPage(page)
                    val newPage = document.startPage(pageInfo)
                    canvas = newPage.canvas
                    yPosition = 50f
                }

                paint.isFakeBoldText = true
                canvas.drawText("${question.question_number} ${question.question_text}", 50f, yPosition, paint)
                yPosition += 20f

                paint.isFakeBoldText = false
                val answer = answers[question.question_number] ?: "No answer provided"
                canvas.drawText("Answer: $answer", 70f, yPosition, paint)
                yPosition += 40f
            }

            document.finishPage(page)

            // Save the document
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(directory, "section_${sectionId}_inspection_${System.currentTimeMillis()}.pdf")
            document.writeTo(FileOutputStream(file))
            document.close()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "PDF exported successfully to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("PDF Export", "Error exporting PDF: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error exporting PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// Data Classes
data class QuestionnaireData(
    val questions: List<Question>
)

data class Question(
    val index: Int,
    val section: String,
    val question_number: String,
    val question_text: String,
    val possible_answers: List<String>?,
    val guide_to_inspection: String
)