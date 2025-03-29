package nkdevelopment.net.sire_questions

/**
 * Data class representing a summary of an inspection for display in UI
 */
data class InspectionSummary(
    val inspectionName: String,
    val lastModified: String,
    var completionPercentage: Float, // Changed to var to allow updating
    val totalSections: Int,
    val sectionsStarted: Int,
    val vessel: String = "" // Add vessel name
)