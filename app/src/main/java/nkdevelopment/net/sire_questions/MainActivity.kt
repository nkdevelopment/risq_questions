package nkdevelopment.net.sire_questions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.shadow
import nkdevelopment.net.sire_questions.ui.theme.InspectionAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InspectionAppTheme {
                MainScreen(onSectionSelected = { sectionId ->
                    val intent = Intent(this, SectionActivity::class.java).apply {
                        putExtra("SECTION_ID", sectionId)
                    }
                    startActivity(intent)
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onSectionSelected: (Int) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vessel Inspection Questionnaire", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.padding(start = 8.dp)
                    )
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
            Text(
                text = "Select a Section",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sections) { section ->
                    SectionCard(section = section, onSectionSelected = onSectionSelected)
                }
            }
        }
    }
}

@Composable
fun SectionCard(section: Section, onSectionSelected: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clickable { onSectionSelected(section.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.id.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(40.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
                textAlign = TextAlign.Center
            )

            Column {
                Text(
                    text = section.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = section.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

data class Section(
    val id: Int,
    val title: String,
    val description: String
)

val sections = listOf(
    Section(1, "General Information", "Basic vessel and inspection details"),
    Section(2, "Certification and Personnel Management", "Vessel certificates and crew documentation"),
    Section(3, "Navigation", "Navigation equipment and procedures"),
    Section(4, "ISM System", "Safety management systems and documentation"),
    Section(5, "Pollution Prevention and Control", "Environmental protection measures"),
    Section(6, "Fire Safety", "Fire prevention and firefighting systems"),
    Section(71, "Fuel Management (Oil)", "Oil fuel handling and storage"),
    Section(72, "Fuel Management (Alternative Fuel-LNG)", "LNG fuel systems and procedures"),
    Section(73, "Fuel Management (Alternative Fuel-Methane)", "Methane fuel handling and systems"),
    Section(74, "Fuel Management (Alternative Fuel-Ammonia)", "Ammonia fuel management"),
    Section(81, "Cargo Operation-Solid Bulk Cargo other than Grain", "Solid bulk cargo handling procedures"),
    Section(82, "Cargo Operation-Bulk Grain", "Grain cargo operations and safety"),
    Section(83, "Cargo Operation-General Cargo", "General cargo handling procedures"),
    Section(84, "Cargo Operation-Cellular Container Ships", "Container stowage and handling"),
    Section(85, "Cargo Operation-Self-Unloading Transshipment", "Self-unloading systems and procedures"),
    Section(9, "Hatch Cover and Lifting Appliances", "Hatch cover operations and maintenance"),
    Section(91, "Gantry Cranes", "Gantry crane operations and safety"),
    Section(10, "Mooring Operations", "Mooring equipment and procedures"),
    Section(11, "Radio and Communication", "Communication systems and procedures"),
    Section(12, "Security", "Security measures and protocols"),
    Section(13, "Machinery Space", "Engine room and machinery systems"),
    Section(14, "General Appearance - Hull and Superstructure", "Hull condition and maintenance"),
    Section(15, "Health and Welfare of Seafarers", "Crew accommodation and welfare"),
    Section(16, "Ice or Polar Water Operations", "Operations in ice conditions"),
    Section(17, "Ship To Ship Operation", "Ship to ship transfer procedures")
)

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    InspectionAppTheme {
        MainScreen(onSectionSelected = {})
    }
}