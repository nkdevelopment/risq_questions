package nkdevelopment.net.sire_questions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import nkdevelopment.net.sire_questions.ui.theme.InspectionAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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

    // PDF file path and dialog state
    var pdfFilePath by remember { mutableStateOf("") }
    var showOpenPdfDialog by remember { mutableStateOf(false) }

    // Load questions from JSON file
    LaunchedEffect(sectionId) {
        try {
            val jsonString = context.assets.open(sectionJsonFileName).bufferedReader().use { it.readText() }
            val type = object : TypeToken<QuestionnaireData>() {}.type
            val data = Gson().fromJson<QuestionnaireData>(jsonString, type)
            questions.clear()
            questions.addAll(data.questions)
            Log.d("SectionActivity", "Successfully loaded ${data.questions.size} questions from $sectionJsonFileName")
        } catch (e: Exception) {
            Log.e("SectionActivity", "Error loading JSON: ${e.message}")
            // If section_1.json file exists in assets, use it for testing purposes
            try {
                val jsonString = context.assets.open("section_1.json").bufferedReader().use { it.readText() }
                val type = object : TypeToken<QuestionnaireData>() {}.type
                val data = Gson().fromJson<QuestionnaireData>(jsonString, type)
                questions.clear()
                questions.addAll(data.questions)
                Log.d("SectionActivity", "Loaded fallback data with ${questions.size} questions")
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
                                    exportToPdf(
                                        context,
                                        sectionId,
                                        questions,
                                        answers,
                                        onPdfCreated = { filePath ->
                                            pdfFilePath = filePath
                                            showOpenPdfDialog = true
                                        }
                                    )
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

                    // Debug button
                    Button(
                        onClick = {
                            // Debug information
                            Log.d("Section Debug", "Section ID: $sectionId")
                            Log.d("Section Debug", "Number of questions: ${questions.size}")
                            Log.d("Section Debug", "Number of answers: ${answers.size}")

                            // Log all questions and answers
                            questions.forEach { question ->
                                val answer = answers[question.question_number] ?: "No answer"
                                Log.d("Section Debug", "Q: ${question.question_number} - ${question.question_text}")
                                Log.d("Section Debug", "A: $answer")
                            }

                            Toast.makeText(
                                context,
                                "Debug info logged. Questions: ${questions.size}, Answers: ${answers.size}",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Debug Info", fontSize = 16.sp)
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

            // Open PDF Dialog
            if (showOpenPdfDialog) {
                AlertDialog(
                    onDismissRequest = { showOpenPdfDialog = false },
                    title = { Text("PDF Created Successfully") },
                    text = {
                        Column {
                            Text("Your PDF has been created.")
                            Text(
                                text = "Location: $pdfFilePath",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                try {
                                    val file = File(pdfFilePath)
                                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )
                                    } else {
                                        Uri.fromFile(file)
                                    }

                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        flags = Intent.FLAG_ACTIVITY_NO_HISTORY or
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }

                                    try {
                                        context.startActivity(intent)
                                    } catch (e: ActivityNotFoundException) {
                                        Toast.makeText(
                                            context,
                                            "No PDF viewer app found. Please install one from the Play Store.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }

                                    showOpenPdfDialog = false
                                } catch (e: Exception) {
                                    Log.e("PDF Viewer", "Error opening PDF: ${e.message}")
                                    Toast.makeText(
                                        context,
                                        "Error opening PDF: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        ) {
                            Text("Open PDF")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOpenPdfDialog = false }) {
                            Text("Close")
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

suspend fun exportToPdf(
    context: Context,
    sectionId: Int,
    questions: List<Question>,
    answers: Map<String, String>,
    onPdfCreated: (String) -> Unit  // Callback that will be called with the file path
) {
    withContext(Dispatchers.IO) {
        var filePath = ""
        try {
            Log.d("PDF Export", "Starting PDF export for section $sectionId")
            Log.d("PDF Export", "Number of questions: ${questions.size}")
            Log.d("PDF Export", "Number of answers: ${answers.size}")

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            val paint = android.graphics.Paint()
            paint.color = android.graphics.Color.BLACK
            paint.textSize = 12f

            // Title
            paint.textSize = 18f
            paint.isFakeBoldText = true
            val sectionTitle = sections.find { it.id == sectionId }?.title ?: "Unknown Section"
            canvas.drawText("Section $sectionId: $sectionTitle", 50f, 50f, paint)
            Log.d("PDF Export", "Drew section title")

            // Date
            paint.textSize = 12f
            paint.isFakeBoldText = false
            val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
            canvas.drawText("Date: ${dateFormat.format(Date())}", 50f, 80f, paint)

            // Questions and Answers
            var yPosition = 120f
            var pageNum = 1

            questions.forEachIndexed { index, question ->
                Log.d("PDF Export", "Processing Q${index+1}: ${question.question_number}")

                // Check if we need a new page
                if (yPosition > 750f) {
                    // Finish current page
                    document.finishPage(page)
                    Log.d("PDF Export", "Finished page $pageNum")

                    // Start a new page
                    pageNum++
                    val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                    page = document.startPage(newPageInfo)
                    canvas = page.canvas
                    yPosition = 50f
                    Log.d("PDF Export", "Started page $pageNum")
                }

                // Draw question
                paint.isFakeBoldText = true
                canvas.drawText("${question.question_number} ${question.question_text}", 50f, yPosition, paint)
                yPosition += 20f

                // Draw answer
                paint.isFakeBoldText = false
                val answer = answers[question.question_number] ?: "No answer provided"
                Log.d("PDF Export", "Answer for ${question.question_number}: $answer")

                // Handle long answers by breaking them into multiple lines
                val maxLineWidth = 475 // Maximum width for text line
                val words = answer.split(" ")
                var line = "Answer: "
                var lineYPosition = yPosition

                words.forEach { word ->
                    val testLine = "$line $word"
                    val testWidth = paint.measureText(testLine)

                    if (testWidth > maxLineWidth) {
                        // Draw current line and start a new one
                        canvas.drawText(line, 70f, lineYPosition, paint)
                        lineYPosition += 15f
                        line = word

                        // Check if we need a new page after adding a line
                        if (lineYPosition > 750f) {
                            document.finishPage(page)
                            Log.d("PDF Export", "Finished page $pageNum due to long answer")
                            pageNum++
                            val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                            page = document.startPage(newPageInfo)
                            canvas = page.canvas
                            lineYPosition = 50f
                            Log.d("PDF Export", "Started page $pageNum for continuing answer")
                        }
                    } else {
                        line = testLine
                    }
                }

                // Draw the last line of the answer
                if (line.isNotEmpty()) {
                    canvas.drawText(line, 70f, lineYPosition, paint)
                }

                yPosition = lineYPosition + 40f
            }

            // Finish the last page
            document.finishPage(page)
            Log.d("PDF Export", "Finished final page $pageNum")

            // Save the document
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "section_${sectionId}_inspection_${timestamp}.pdf"

            // First try to save to app's private storage (most reliable)
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (directory == null) {
                Log.e("PDF Export", "External files directory is null")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: Cannot access external storage", Toast.LENGTH_LONG).show()
                }
                return@withContext
            }

            val file = File(directory, fileName)
            filePath = file.absolutePath
            Log.d("PDF Export", "Saving to file: $filePath")

            try {
                val fileOutputStream = FileOutputStream(file)
                document.writeTo(fileOutputStream)
                fileOutputStream.flush()
                fileOutputStream.close()
                Log.d("PDF Export", "Successfully wrote to file output stream")
            } catch (e: Exception) {
                Log.e("PDF Export", "Error writing to file: ${e.message}", e)
                throw e  // Rethrow to be caught by the outer try-catch
            }

            document.close()
            Log.d("PDF Export", "Closed PDF document")
            Log.d("PDF Export", "Final file path: $filePath")

            withContext(Dispatchers.Main) {
                // Call the callback with the file path
                onPdfCreated(filePath)

                Toast.makeText(
                    context,
                    "PDF exported successfully",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e("PDF Export", "Error exporting PDF: ${e.message}", e)
            e.printStackTrace() // Print the full stack trace for more details

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Error exporting PDF: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
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