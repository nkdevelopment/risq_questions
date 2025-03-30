package nkdevelopment.net.risq_questions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nkdevelopment.net.risq_questions.ui.theme.InspectionAppTheme
import java.io.File
import java.io.IOException

class SectionActivity : ComponentActivity() {
    private lateinit var repository: InspectionRepository
    private var saveJob: Job? = null

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    // Add photo capture utility
    private lateinit var photoCaptureUtil: PhotoCaptureUtil

    // Callback for camera permission result
    private var cameraPermissionResultCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sectionId = intent.getIntExtra("SECTION_ID", 1)
        val inspectionName = intent.getStringExtra("INSPECTION_NAME") ?: ""

        Log.d("SectionActivity", "Starting section $sectionId for inspection $inspectionName")

        // Initialize repository with error handling
        try {
            repository = InspectionRepository(this)
        } catch (e: Exception) {
            Log.e("SectionActivity", "Error initializing repository", e)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // Return to previous activity gracefully
            return
        }

        // Initialize photo capture utility
        try {
            photoCaptureUtil = PhotoCaptureUtil(this)
            photoCaptureUtil.initialize { questionNumber, photoUri ->
                // This will be called when a photo is successfully captured
                Toast.makeText(this, "Photo saved for question $questionNumber", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("SectionActivity", "Error initializing photo capture", e)
            Toast.makeText(this, "Camera functionality may not work properly", Toast.LENGTH_SHORT).show()
            // Continue without crashing, as photos are not critical functionality
        }

        setContent {
            InspectionAppTheme {
                SectionScreen(
                    sectionId = sectionId,
                    inspectionName = inspectionName,
                    onBackClicked = {
                        // Use lifecycleScope to wait for any pending saves before navigating back
                        lifecycleScope.launch {
                            saveJob?.join() // Wait for pending save operations
                            finish()
                        }
                    },
                    context = this,
                    repository = repository,
                    onSaveJobUpdated = { job ->
                        saveJob = job
                        Log.d("SectionActivity", "Save job updated: ${job?.isActive}")
                    },
                    onTakePhoto = { questionNumber ->
                        // Launch camera to take a photo for this question
                        try {
                            photoCaptureUtil.takePhoto(questionNumber)
                        } catch (e: Exception) {
                            Log.e("SectionActivity", "Error taking photo", e)
                            Toast.makeText(this, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    /**
     * Set callback for camera permission result
     */
    fun setCameraPermissionResultCallback(callback: (Boolean) -> Unit) {
        cameraPermissionResultCallback = callback
    }

    /**
     * Handle permission request results
     */
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                val permissionGranted = grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED

                Log.d("SectionActivity", "Camera permission ${if (permissionGranted) "granted" else "denied"}")

                // Notify the callback
                cameraPermissionResultCallback?.invoke(permissionGranted)

                if (!permissionGranted) {
                    // Show an explanation if needed
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {
                        Toast.makeText(
                            this,
                            "Camera permission is required to take photos for the inspection",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // User has denied permission and checked "Don't ask again"
                        Toast.makeText(
                            this,
                            "Camera permission denied. You can enable it in app settings.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // Override onBackPressed to ensure saves complete before navigating back
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (saveJob != null && saveJob?.isActive == true) {
            // Use lifecycleScope since we're outside of a composable
            lifecycleScope.launch {
                Log.d("SectionActivity", "Waiting for save job to complete before navigating back")
                // Wait for any pending saves to complete
                saveJob?.join()
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }
}

@Composable
fun QuestionItem(
    question: Question,
    answer: String,
    comment: String,
    onAnswerChanged: (String) -> Unit,
    onCommentChanged: (String) -> Unit,
    onGuideClicked: () -> Unit,
    onTakePhoto: () -> Unit
) {
    val isAnswered = answer.isNotBlank()
    val possibleAnswers = question.possible_answers
    var showCommentField by remember { mutableStateOf(comment.isNotBlank()) }
    val maxCommentLength = 500 // Character limit for comments

    // Generate background color based on answered state
    val cardColor = if (isAnswered) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
    }

    // Generate border color based on answered state
    val borderColor = if (isAnswered) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isAnswered) 1.dp else 0.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp) // Use consistent spacing
        ) {
            // Question header with number, text, and answer status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Question number badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        text = question.question_number,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = question.question_text,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                // Show photo icon if photo field is true
                if (question.photo) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Photo Required",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(horizontal = 4.dp)
                    )
                }

                // Status indicator
                if (isAnswered) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Answered",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(start = 4.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Unanswered",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(start = 4.dp)
                    )
                }
            }

            // Answer options
            if (possibleAnswers != null && possibleAnswers.isNotEmpty()) {
                // Radio buttons for predefined answers
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        possibleAnswers.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onAnswerChanged(option) }
                                    .background(
                                        if (answer == option)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else
                                            Color.Transparent
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = answer == option,
                                    onClick = { onAnswerChanged(option) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (answer == option)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
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
                    placeholder = { Text("Type your response here") },
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isAnswered)
                            MaterialTheme.colorScheme.outline
                        else
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // Comment section
            AnimatedVisibility(
                visible = showCommentField,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = {
                        if (it.length <= maxCommentLength) {
                            onCommentChanged(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Comment") },
                    placeholder = { Text("Add a comment or note about this question") },
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    supportingText = {
                        Text(
                            text = "${comment.length}/$maxCommentLength",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            color = if (comment.length >= maxCommentLength)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            // COMPLETELY REDESIGNED ACTION BUTTONS
            // Use a FlowRow-like approach with wrapped buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Make buttons have equal heights and compact width
                val buttonModifier = Modifier.height(36.dp)

                // Comment button (compact)
                TextButton(
                    onClick = { showCommentField = !showCommentField },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = buttonModifier,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (comment.isNotBlank())
                            Icons.Default.Comment
                        else
                            Icons.Default.AddComment,
                        contentDescription = "Comment",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showCommentField) "Hide" else if (comment.isNotBlank()) "Edit" else "Comment",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Photo button (compact)
                if (question.photo) {
                    TextButton(
                        onClick = onTakePhoto,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = buttonModifier,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Take Photo",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Photo",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Guide button (compact)
                TextButton(
                    onClick = onGuideClicked,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = buttonModifier,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Guide to Inspection",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Guide",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(
    sectionId: Int,
    inspectionName: String,
    onBackClicked: () -> Unit,
    context: Context,
    repository: InspectionRepository,
    onSaveJobUpdated: (Job?) -> Unit,
    onTakePhoto: (String) -> Unit  // Add this parameter
) {
    val coroutineScope = rememberCoroutineScope()
    val sectionJsonFileName = "section_${sectionId}.json"

    // Questionnaire state
    val questions = remember { mutableStateListOf<Question>() }
    val answers = remember { mutableStateMapOf<String, String>() }
    val comments = remember { mutableStateMapOf<String, String>() } // Add this for comments

    // Guide dialog state
    var showGuideDialog by remember { mutableStateOf(false) }
    var currentGuideText by remember { mutableStateOf("") }
    var currentQuestionText by remember { mutableStateOf("") }

    // Show all unanswered questions state
    var showUnansweredWarning by remember { mutableStateOf(false) }

    // PDF file path and dialog state
    var pdfFilePath by remember { mutableStateOf("") }
    var showOpenPdfDialog by remember { mutableStateOf(false) }

    // Loading state
    var isLoading by remember { mutableStateOf(true) }

    // Error state
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Debounce for auto-save
    var saveJob by remember { mutableStateOf<Job?>(null) }

    // New save status indicator states
    var saveStatus by remember { mutableStateOf<String?>(null) }
    var showSaveStatus by remember { mutableStateOf(false) }

    // New state for tracking pending saves
    var hasPendingSaves by remember { mutableStateOf(false) }

    // New state for unsaved changes dialog
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    // Update parent activity whenever saveJob changes
    LaunchedEffect(saveJob) {
        onSaveJobUpdated(saveJob)
        hasPendingSaves = saveJob?.isActive == true
    }

    // Handle back button with custom behavior
    BackHandler(enabled = hasPendingSaves) {
        showUnsavedChangesDialog = true
    }

    // Function to handle saving with visual feedback
    val saveResponse = { questionNumber: String, newAnswer: String, newComment: String ->
        // Cancel previous save job if it exists
        saveJob?.cancel()

        // Mark that we have pending saves
        hasPendingSaves = true

        // Create a new save job with debounce
        saveJob = coroutineScope.launch {
            try {
                // Set saving status
                saveStatus = "Saving..."
                showSaveStatus = true

                // Wait for debounce period
                delay(300)

                // Get the section name
                val section = SectionData.sections.find { it.id == sectionId }
                val sectionTitle = section?.title ?: ""
                val sectionKey = "$sectionId: $sectionTitle"

                // Save the response with comment
                val saved = repository.updateResponse(
                    inspectionName = inspectionName,
                    sectionId = sectionId.toString(),
                    questionNumber = questionNumber,
                    answer = newAnswer,
                    comments = newComment
                )

                // Update UI based on save result
                if (saved) {
                    Log.d("SectionActivity", "Saved answer for question $questionNumber")
                    saveStatus = "Saved"

                    // For specific fields that should update the main inspection object
                    if (sectionId == 1 && (questionNumber == "1.1" || questionNumber == "1.22")) {
                        // Update vessel name or inspector name in the inspection object
                        repository.updateInspectionMetadata(inspectionName)
                    }
                } else {
                    Log.e("SectionActivity", "Failed to save answer for question $questionNumber")
                    saveStatus = "Save failed"
                }

                // Show save status briefly
                showSaveStatus = true
                delay(1000)
                showSaveStatus = false

                // Mark that we no longer have pending saves
                hasPendingSaves = false
            } catch (e: Exception) {
                Log.e("SectionActivity", "Error saving response: ${e.message}")
                saveStatus = "Error: ${e.message}"
                showSaveStatus = true
                delay(2000)
                showSaveStatus = false
                hasPendingSaves = false
            }
        }
    }

    // Load questions from JSON file and saved answers
    LaunchedEffect(sectionId) {
        try {
            // Debug statement to list available assets
            try {
                val assetList = context.assets.list("") ?: emptyArray()
                Log.d("SectionActivity", "Available assets: ${assetList.joinToString()}")
            } catch (e: Exception) {
                Log.e("SectionActivity", "Error listing assets", e)
            }

            // Load questions from assets with error handling
            Log.d("SectionActivity", "Attempting to load section $sectionId with file $sectionJsonFileName")

            val jsonString = try {
                context.assets.open(sectionJsonFileName).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                Log.e("SectionActivity", "Failed to load JSON file: $sectionJsonFileName", e)
                hasError = true
                errorMessage = "Failed to load questions: ${e.message}"
                // Return an empty JSON structure instead of crashing
                """{"questions":[]}"""
            }

            val type = object : TypeToken<QuestionnaireData>() {}.type
            val data = try {
                Gson().fromJson<QuestionnaireData>(jsonString, type)
            } catch (e: JsonSyntaxException) {
                Log.e("SectionActivity", "Error parsing JSON: ${e.message}")
                hasError = true
                errorMessage = "Invalid question format: ${e.message}"
                QuestionnaireData(emptyList())
            }

            questions.clear()
            questions.addAll(data.questions)

            if (questions.isEmpty() && !hasError) {
                Log.w("SectionActivity", "No questions found for section $sectionId")
                hasError = true
                errorMessage = "No questions found for this section."
            }

            // Load saved answers for this inspection and section
            try {
                val inspection = repository.getInspection(inspectionName)
                if (inspection != null) {
                    // Get section name
                    val section = SectionData.sections.find { it.id == sectionId }
                    val sectionTitle = section?.title ?: ""

                    // Try both formats of section key (with and without section name)
                    val sectionKey = "$sectionId: $sectionTitle"
                    val sectionResponsesWithName = inspection.responses[sectionKey] ?: emptyList()
                    val sectionResponsesWithoutName = inspection.responses[sectionId.toString()] ?: emptyList()

                    // Use responses with section name if available, otherwise use old format
                    val sectionResponses = if (sectionResponsesWithName.isNotEmpty())
                        sectionResponsesWithName else sectionResponsesWithoutName

                    // Populate answers map with saved responses
                    sectionResponses.forEach { response ->
                        answers[response.questionNumber] = response.answer
                        comments[response.questionNumber] = response.comments // Load comments
                    }

                    Log.d("SectionActivity", "Loaded inspection with ${sectionResponses.size} answers for section $sectionId")
                } else {
                    // Create a new inspection if it doesn't exist
                    val newInspection = Inspection.createNew(inspectionName)
                    repository.saveInspection(newInspection)
                    Log.d("SectionActivity", "Created new inspection: $inspectionName")
                }
            } catch (e: Exception) {
                Log.e("SectionActivity", "Error loading inspection data: ${e.message}")
                // Don't set error state here, as we can still show questions even if answers failed to load
            }

            isLoading = false
        } catch (e: Exception) {
            Log.e("SectionActivity", "Error in LaunchedEffect: ${e.message}")
            hasError = true
            errorMessage = "Failed to initialize: ${e.message}"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Section $sectionId Questions",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        if (inspectionName.isNotBlank()) {
                            Text(
                                text = inspectionName,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasPendingSaves) {
                            showUnsavedChangesDialog = true
                        } else {
                            onBackClicked()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    // Progress indicator showing completion rate
                    val answeredCount = answers.size
                    val totalCount = questions.size
                    if (totalCount > 0) {
                        val progress = answeredCount.toFloat() / totalCount.toFloat()

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isLoading && !hasError && questions.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val unansweredQuestions = questions.filter {
                            answers[it.question_number].isNullOrBlank() &&
                                    it.possible_answers?.contains("N/A") != true
                        }

                        if (unansweredQuestions.isEmpty()) {
                            coroutineScope.launch {
                                try {
                                    exportToPdf(
                                        context,
                                        sectionId,
                                        questions,
                                        answers,
                                        comments, // Pass comments to export function
                                        inspectionName,
                                        onPdfCreated = { filePath ->
                                            pdfFilePath = filePath
                                            showOpenPdfDialog = true
                                        }
                                    )
                                } catch (e: Exception) {
                                    Log.e("SectionActivity", "Error creating PDF: ${e.message}")
                                    Toast.makeText(context, "Failed to create PDF: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            showUnansweredWarning = true
                        }
                    },
                    icon = {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "Export PDF"
                        )
                    },
                    text = { Text("Export PDF") },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(60.dp),
                            strokeWidth = 5.dp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Loading questions...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (hasError) {
                // Error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(80.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Something went wrong",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onBackClicked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Go Back")
                        }
                    }
                }
            } else if (questions.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SentimentDissatisfied,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No questions found for this section",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Please try another section or contact support",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Section title and questions list
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Section title and info
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Section icon/number
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "$sectionId",
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = SectionData.sections.find { it.id == sectionId }?.title
                                        ?: "Section $sectionId",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                // Display section description
                                Text(
                                    text = SectionData.sections.find { it.id == sectionId }?.description
                                        ?: "",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Questions with answers count summary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Questions (${questions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Answered vs total count
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )

                            Text(
                                text = " ${answers.size}/${questions.size} Answered",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Questions list
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(questions) { index, question ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(durationMillis = 300, delayMillis = index * 30)) +
                                        slideInVertically(
                                            initialOffsetY = { 50 },
                                            animationSpec = tween(
                                                durationMillis = 300,
                                                delayMillis = index * 30
                                            )
                                        )
                            ) {
                                QuestionItem(
                                    question = question,
                                    answer = answers[question.question_number] ?: "",
                                    comment = comments[question.question_number] ?: "",
                                    onAnswerChanged = { newAnswer ->
                                        // Update the answers map right away for immediate UI update
                                        answers[question.question_number] = newAnswer

                                        // Call our enhanced save function
                                        saveResponse(
                                            question.question_number,
                                            newAnswer,
                                            comments[question.question_number] ?: ""
                                        )
                                    },
                                    onCommentChanged = { newComment ->
                                        // Update the comments map right away for immediate UI update
                                        comments[question.question_number] = newComment

                                        // Call our enhanced save function
                                        saveResponse(
                                            question.question_number,
                                            answers[question.question_number] ?: "",
                                            newComment
                                        )
                                    },
                                    onGuideClicked = {
                                        currentGuideText = question.guide_to_inspection
                                        currentQuestionText =
                                            "${question.question_number} ${question.question_text}"
                                        showGuideDialog = true
                                    },
                                    onTakePhoto = {
                                        // Call the photo capture function with the question number
                                        onTakePhoto(question.question_number)
                                    }
                                )
                            }
                        }

                        // Add some bottom padding for FAB
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }

            // Save status indicator
            AnimatedVisibility(
                visible = showSaveStatus,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 80.dp),
                    color = when {
                        saveStatus == "Saved" -> MaterialTheme.colorScheme.tertiary
                        saveStatus == "Saving..." -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (saveStatus == "Saving...") {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else if (saveStatus == "Saved") {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = saveStatus ?: "",
                            color = when {
                                saveStatus == "Saved" -> MaterialTheme.colorScheme.onTertiary
                                saveStatus == "Saving..." -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onError
                            }
                        )
                    }
                }
            }
        }

        // Guide Dialog with improved UI
        if (showGuideDialog) {
            Dialog(onDismissRequest = { showGuideDialog = false }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 6.dp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Header with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )

                            Text(
                                text = "Guide to Inspection",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Question reference
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = currentQuestionText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        // Guide content
                        Text(
                            text = currentGuideText.ifEmpty { "No guide information available." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Close button
                        Button(
                            onClick = { showGuideDialog = false },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }

        // Unanswered Questions Warning with improved UI
        if (showUnansweredWarning) {
            AlertDialog(
                onDismissRequest = { showUnansweredWarning = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text("Unanswered Questions")
                },
                text = {
                    Text(
                        "There are unanswered mandatory questions. Please complete all required fields before exporting to PDF.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showUnansweredWarning = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        // Open PDF Dialog with improved UI
        if (showOpenPdfDialog) {
            AlertDialog(
                onDismissRequest = { showOpenPdfDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                },
                title = {
                    Text(
                        "PDF Created Successfully",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Your inspection report has been exported as a PDF file.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = pdfFilePath,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
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
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Open PDF")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showOpenPdfDialog = false }
                    ) {
                        Text("Close")
                    }
                }
            )
        }

        // Unsaved changes dialog
        if (showUnsavedChangesDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedChangesDialog = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("Unsaved Changes") },
                text = { Text("You have unsaved changes. Wait for saving to complete or discard changes?") },
                confirmButton = {
                    Button(
                        onClick = {
                            // Cancel active save job if exists
                            saveJob?.cancel()
                            showUnsavedChangesDialog = false
                            onBackClicked()
                        }
                    ) {
                        Text("Discard and Exit")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showUnsavedChangesDialog = false }
                    ) {
                        Text("Wait")
                    }
                }
            )
        }
    }
}