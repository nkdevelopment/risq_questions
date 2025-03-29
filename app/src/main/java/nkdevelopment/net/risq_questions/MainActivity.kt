package nkdevelopment.net.risq_questions

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nkdevelopment.net.risq_questions.ui.theme.InspectionAppTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.background
import android.content.Context

class MainActivity : ComponentActivity() {
    private lateinit var repository: InspectionRepository
    // Add a refreshKey to force refreshes
    private var refreshKey = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = InspectionRepository(this)

        loadUI()
    }

    private fun loadUI() {
        // Increment the refresh key to ensure complete data reload
        refreshKey++

        setContent {
            InspectionAppTheme {
                MainScreen(
                    repository = repository,
                    refreshKey = refreshKey, // Pass the refresh key to MainScreen
                    onStartNewInspection = { inspectionName ->
                        val intent = Intent(this, NewInspectionActivity::class.java).apply {
                            putExtra("INSPECTION_NAME", inspectionName)
                        }
                        startActivity(intent)
                    },
                    onContinueInspection = { inspectionName ->
                        val intent = Intent(this, NewInspectionActivity::class.java).apply {
                            putExtra("INSPECTION_NAME", inspectionName)
                        }
                        startActivity(intent)
                    },
                    context = this // Pass the context
                )
            }
        }
    }

    // Ensure we fully refresh the UI when returning to this activity
    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called - refreshing inspections list")
        // Force a complete UI reload to ensure we get fresh data
        loadUI()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: InspectionRepository,
    refreshKey: Int, // Add refresh key parameter
    onStartNewInspection: (String) -> Unit,
    onContinueInspection: (String) -> Unit,
    context: Context // Add this parameter
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showNewInspectionDialog by remember { mutableStateOf(false) }
    var splashVisible by remember { mutableStateOf(true) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var inspectionToDelete by remember { mutableStateOf<String?>(null) }

    // Upload state variables
    var isUploading by remember { mutableStateOf(false) }
    var uploadResponse by remember { mutableStateOf<Result<String>?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }

    // State for list of inspections
    var inspections by remember { mutableStateOf<List<Inspection>>(emptyList()) }
    var inspectionSummaries by remember { mutableStateOf<List<InspectionSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load inspections - use refreshKey to force recomposition
    LaunchedEffect(refreshKey) {
        // Hide splash after delay
        if (splashVisible) {
            kotlinx.coroutines.delay(1500)
            splashVisible = false
        }

        // Collect inspections
        isLoading = true
        repository.getAllInspections().collectLatest { list ->
            inspections = list
            Log.d("MainScreen", "Loaded ${list.size} inspections with refresh key: $refreshKey")

            // Calculate summaries for each inspection
            val summaries = mutableListOf<InspectionSummary>()

            list.forEach { inspection ->
                // Log detailed info for debugging
                Log.d("MainScreen", "Processing inspection: ${inspection.inspectionName}, vessel: '${inspection.vessel}'")

                // Calculate completion percentage
                val completion = repository.calculateCompletionPercentage(inspection)

                // Get count of sections with answers - checking both key formats
                var sectionsStarted = 0
                SectionData.sections.forEach { section ->
                    val sectionId = section.id.toString()
                    val sectionTitle = section.title
                    val sectionKey = "$sectionId: $sectionTitle"

                    if (inspection.responses.containsKey(sectionKey) || inspection.responses.containsKey(sectionId)) {
                        sectionsStarted++
                    }
                }

                // Create inspection summary with vessel name
                val summary = InspectionSummary(
                    inspectionName = inspection.inspectionName,
                    lastModified = inspection.inspectionDate,
                    completionPercentage = completion,
                    totalSections = SectionData.sections.size,
                    sectionsStarted = sectionsStarted,
                    vessel = inspection.vessel // Make sure vessel name is passed
                )

                Log.d("MainScreen", "Adding summary for ${inspection.inspectionName}, vessel: ${inspection.vessel}")
                summaries.add(summary)
            }

            inspectionSummaries = summaries
            isLoading = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // Drawer content
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                // Drawer header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Vessel Inspection",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            "SIRE Questionnaire",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Menu items
                NavigationDrawerItem(
                    label = { Text("New Inspection") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            showNewInspectionDialog = true
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    badge = { },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                NavigationDrawerItem(
                    label = { Text("About") },
                    selected = false,
                    onClick = { /* Show about dialog */ },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    badge = { },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Vessel Inspection",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showNewInspectionDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Inspection",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showNewInspectionDialog = true },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Inspection")
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = !splashVisible,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessLow)),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Main content - Show list of inspections or welcome screen
                        if (isLoading) {
                            // Loading state
                            CircularProgressIndicator(
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (inspectionSummaries.isEmpty()) {
                            // Empty state - No inspections
                            EmptyInspectionsView(onCreateNew = { showNewInspectionDialog = true })
                        } else {
                            // List of inspections
                            Text(
                                text = "Your Inspections",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Start
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(inspectionSummaries) { summary ->
                                    // Add a key parameter to force recomposition when data changes
                                    key(summary.inspectionName, summary.vessel, summary.lastModified) {
                                        InspectionCard(
                                            summary = summary,
                                            onContinue = {
                                                onContinueInspection(summary.inspectionName)
                                            },
                                            onDelete = {
                                                inspectionToDelete = summary.inspectionName
                                                showDeleteConfirmation = true
                                            },
                                            onUpload = {
                                                scope.launch {
                                                    isUploading = true
                                                    uploadResponse = repository.uploadInspection(summary.inspectionName)
                                                    isUploading = false
                                                    showUploadDialog = true
                                                }
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Bottom spacing
                                item {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                    }
                }

                // Splash screen
                AnimatedVisibility(
                    visible = splashVisible,
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        // App logo or icon
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Sailing,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            "Vessel Inspection",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            "SIRE Questionnaire App",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 4.dp
                        )
                    }
                }

                // Upload progress overlay
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .width(300.dp)
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Uploading Inspection...",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }

            // New Inspection Dialog
            if (showNewInspectionDialog) {
                NewInspectionNameDialog(
                    onDismiss = { showNewInspectionDialog = false },
                    onConfirm = { inspectionName ->
                        scope.launch {
                            // Create new inspection
                            val newInspection = Inspection.createNew(inspectionName)
                            repository.saveInspection(newInspection)

                            showNewInspectionDialog = false
                            onStartNewInspection(inspectionName)
                        }
                    }
                )
            }

            // Delete confirmation dialog
            if (showDeleteConfirmation && inspectionToDelete != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteConfirmation = false
                        inspectionToDelete = null
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = {
                        Text("Delete Inspection")
                    },
                    text = {
                        Text("Are you sure you want to delete \"$inspectionToDelete\"? This action cannot be undone.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                inspectionToDelete?.let { name ->
                                    scope.launch {
                                        val success = repository.deleteInspection(name)

                                        if (success) {
                                            // Display success message
                                            Toast.makeText(
                                                context,
                                                "Inspection deleted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            // Show failure message
                                            Toast.makeText(
                                                context,
                                                "Failed to delete inspection",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        // Reset dialog state
                                        showDeleteConfirmation = false
                                        inspectionToDelete = null
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = {
                                showDeleteConfirmation = false
                                inspectionToDelete = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Upload result dialog
            if (showUploadDialog && uploadResponse != null) {
                val clipboardManager = LocalClipboardManager.current

                AlertDialog(
                    onDismissRequest = {
                        showUploadDialog = false
                        uploadResponse = null
                    },
                    icon = {
                        Icon(
                            imageVector = if (uploadResponse?.isSuccess == true)
                                Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (uploadResponse?.isSuccess == true)
                                MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    },
                    title = {
                        Text(
                            text = if (uploadResponse?.isSuccess == true)
                                "Upload Successful" else "Upload Failed"
                        )
                    },
                    text = {
                        Column {
                            if (uploadResponse?.isSuccess == true) {
                                val url = uploadResponse?.getOrNull() ?: ""
                                Text("The inspection has been successfully uploaded to the server.")

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(uploadResponse?.getOrNull() ?: ""))
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy URL",
                                        modifier = Modifier.size(18.dp).padding(end = 4.dp)
                                    )
                                    Text("Copy URL")
                                }
                            } else {
                                Text(
                                    "Failed to upload the inspection: ${uploadResponse?.exceptionOrNull()?.message ?: "Unknown error"}"
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showUploadDialog = false
                                uploadResponse = null
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyInspectionsView(onCreateNew: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FindInPage,
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .padding(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )

        Text(
            text = "No inspections yet",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = "Start a new vessel inspection to begin recording your findings",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = onCreateNew,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Create New Inspection")
        }
    }
}

@Composable
fun InspectionCard(
    summary: InspectionSummary,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onUpload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with inspection name and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = summary.inspectionName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Action buttons
                Row {
                    // Upload button
                    IconButton(
                        onClick = onUpload,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = "Upload inspection",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Display vessel name if available - now it uses key() for proper recomposition
            if (summary.vessel.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBoat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Vessel: ${summary.vessel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Last modified date
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Last edited: ${formatDate(summary.lastModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Completion info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Progress indicator
                Box(modifier = Modifier.weight(1f)) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${(summary.completionPercentage * 100).toInt()}% Complete",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "${summary.sectionsStarted}/${summary.totalSections} sections",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { summary.completionPercentage },
                            modifier = Modifier.fillMaxWidth(),
                            color = when {
                                summary.completionPercentage > 0.7f -> MaterialTheme.colorScheme.tertiary
                                summary.completionPercentage > 0.3f -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continue button
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Continue Inspection")
            }
        }
    }
}

@Composable
fun NewInspectionNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var inspectionName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "New Inspection",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = inspectionName,
                    onValueChange = {
                        inspectionName = it
                        isError = false
                    },
                    label = { Text("Inspection Name") },
                    placeholder = { Text("Enter a name for this inspection") },
                    singleLine = true,
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = "Please enter a name for the inspection",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (inspectionName.isBlank()) {
                            isError = true
                        } else {
                            onConfirm(inspectionName)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text(
                        "Start Inspection",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Cancel",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

/**
 * Format a date string for display
 */
private fun formatDate(dateString: String): String {
    try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

        val date = inputFormat.parse(dateString) ?: return dateString
        return outputFormat.format(date)
    } catch (e: Exception) {
        return dateString
    }
}