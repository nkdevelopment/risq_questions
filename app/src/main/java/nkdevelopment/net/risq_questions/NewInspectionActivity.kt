package nkdevelopment.net.risq_questions

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nkdevelopment.net.risq_questions.ui.theme.InspectionAppTheme

class NewInspectionActivity : ComponentActivity() {
    private lateinit var repository: InspectionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inspectionName = intent.getStringExtra("INSPECTION_NAME") ?: "New Inspection"
        repository = InspectionRepository(this)

        setContent {
            InspectionAppTheme {
                NewInspectionScreen(
                    inspectionName = inspectionName,
                    repository = repository,
                    onBackClicked = { finish() },
                    onSectionSelected = { sectionId ->
                        val intent = Intent(this, SectionActivity::class.java).apply {
                            putExtra("SECTION_ID", sectionId)
                            putExtra("INSPECTION_NAME", inspectionName)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    // Override onResume to refresh the UI when returning to this activity
    override fun onResume() {
        super.onResume()
        Log.d("NewInspectionActivity", "onResume called - refreshing UI data")

        // The Compose UI will recompose with fresh data when we call setContent again
        val inspectionName = intent.getStringExtra("INSPECTION_NAME") ?: "New Inspection"

        setContent {
            InspectionAppTheme {
                NewInspectionScreen(
                    inspectionName = inspectionName,
                    repository = repository,
                    onBackClicked = { finish() },
                    onSectionSelected = { sectionId ->
                        val intent = Intent(this, SectionActivity::class.java).apply {
                            putExtra("SECTION_ID", sectionId)
                            putExtra("INSPECTION_NAME", inspectionName)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewInspectionScreen(
    inspectionName: String,
    repository: InspectionRepository,
    onBackClicked: () -> Unit,
    onSectionSelected: (Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var filteredSections by remember { mutableStateOf(SectionData.sections) }
    var inspection by remember { mutableStateOf<Inspection?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var sectionCompletion by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var overallCompletion by remember { mutableStateOf(0f) }

    // Load inspection and calculate section completion percentages
    LaunchedEffect(inspectionName) {
        isLoading = true

        // Load inspection
        val loadedInspection = repository.getInspection(inspectionName)

        if (loadedInspection != null) {
            inspection = loadedInspection
            Log.d("NewInspectionScreen", "Loaded inspection: ${loadedInspection.inspectionName} with ${loadedInspection.responses.size} sections")

            // Calculate overall completion
            overallCompletion = repository.calculateCompletionPercentage(loadedInspection)

            // Calculate section completion percentages
            val completionMap = mutableMapOf<String, Float>()

            SectionData.sections.forEach { section ->
                val sectionId = section.id.toString()
                val completion = repository.calculateSectionCompletionPercentage(loadedInspection, sectionId)
                completionMap[sectionId] = completion
            }

            sectionCompletion = completionMap
        } else {
            // Create a new inspection if it doesn't exist
            val newInspection = Inspection.createNew(inspectionName)
            repository.saveInspection(newInspection)
            inspection = newInspection
            Log.d("NewInspectionScreen", "Created new inspection: $inspectionName")

            overallCompletion = 0f
            sectionCompletion = emptyMap()
        }

        isLoading = false
    }

    LaunchedEffect(searchQuery) {
        filteredSections = if (searchQuery.isEmpty()) {
            SectionData.sections
        } else {
            SectionData.sections.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Select Section",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = inspectionName,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    // Search icon
                    IconButton(onClick = { /* Toggle search bar visibility */ }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display inspection name card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsBoat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(end = 8.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Inspection:",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = inspectionName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Display overall completion if inspection is loaded
                    if (!isLoading && inspection != null) {
                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { overallCompletion },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        )

                        Text(
                            text = "${(overallCompletion * 100).toInt()}% Complete Overall",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Add count of sections started
                        val sectionsStarted = inspection?.responses?.size ?: 0
                        Text(
                            text = "$sectionsStarted of ${SectionData.sections.size} sections started",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Optional: Add search bar for sections
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search sections...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Text(
                text = "Select a Section to Inspect",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
            )

            if (isLoading) {
                // Show loading indicator
                CircularProgressIndicator(
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                // Section list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(filteredSections) { index, section ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(durationMillis = 300, delayMillis = index * 50)) +
                                    slideInVertically(
                                        initialOffsetY = { 100 },
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            delayMillis = index * 50
                                        )
                                    )
                        ) {
                            InspectionSectionCard(
                                section = section,
                                completion = sectionCompletion[section.id.toString()] ?: 0f,
                                onSectionSelected = onSectionSelected
                            )
                        }
                    }

                    // Add bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun InspectionSectionCard(
    section: Section,
    completion: Float,
    onSectionSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSectionSelected(section.id) },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Section number badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = section.id.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Section title and description
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = section.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = section.description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Show completion progress if started
                    if (completion > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { completion },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp),
                                color = when {
                                    completion > 0.75f -> MaterialTheme.colorScheme.tertiary
                                    completion > 0.3f -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Text(
                                text = "${(completion * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                // Arrow icon
                Icon(
                    imageVector = if (completion > 0) Icons.Default.ArrowForward else Icons.Default.Add,
                    contentDescription = "Select",
                    tint = if (completion > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}