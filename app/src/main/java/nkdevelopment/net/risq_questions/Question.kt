package nkdevelopment.net.risq_questions

/**
 * Data class representing the entire questionnaire data structure from JSON files
 */
data class QuestionnaireData(
    val questions: List<Question>
)

/**
 * Data class representing an individual inspection question
 */
data class Question(
    val index: Int,
    val section: String,
    val question_number: String,
    val question_text: String,
    val possible_answers: List<String>?,
    val guide_to_inspection: String,
    val photo: Boolean = false  // Added photo field with default value
)

/**
 * Data class for storing question responses in a questionnaire
 */
data class QuestionResponse(
    val questionNumber: String,
    val answer: String,
    val comments: String = "",
    val questionText: String = ""  // Added question text field
)

/**
 * Data class representing a complete inspection
 */
data class Inspection(
    val inspectionName: String,
    val inspectionDate: String,
    val vessel: String = "",
    val inspector: String = "",
    val responses: Map<String, List<QuestionResponse>> = mapOf()
) {
    companion object {
        /**
         * Create a new inspection with default values
         */
        fun createNew(inspectionName: String): Inspection {
            return Inspection(
                inspectionName = inspectionName,
                inspectionDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date()),
                vessel = "",
                inspector = "",
                responses = mapOf()
            )
        }
    }
}