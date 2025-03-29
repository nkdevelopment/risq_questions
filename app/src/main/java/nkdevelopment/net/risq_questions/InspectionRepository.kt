package nkdevelopment.net.risq_questions

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository class for managing inspection JSON files in the app's private storage
 */
class InspectionRepository(val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val inspectionsDir = File(context.filesDir, "inspections")

    // Try multiple possible URLs for the server - will try these in order
    private val UPLOAD_URLS = listOf(
        "http://nkdevelopment.net/app_sire_questions/",
        "http://nkdevelopment.net/app_sire_questions/upload.php",
        "https://nkdevelopment.net/app_sire_questions/"
    )

    init {
        // Create the inspections directory if it doesn't exist
        if (!inspectionsDir.exists()) {
            inspectionsDir.mkdirs()
        }
    }

    /**
     * Get all saved inspections
     */
    fun getAllInspections(): Flow<List<Inspection>> = flow {
        val inspections = mutableListOf<Inspection>()

        try {
            if (inspectionsDir.exists()) {
                val files = inspectionsDir.listFiles { file -> file.extension == "json" }

                // Handle the case when there are no files
                if (files == null || files.isEmpty()) {
                    emit(emptyList<Inspection>())
                    return@flow
                }

                files.forEach { file ->
                    try {
                        val inspection = loadInspectionFromFile(file)
                        inspections.add(inspection)
                    } catch (e: Exception) {
                        Log.e(
                            "InspectionRepo",
                            "Error loading inspection from ${file.name}: ${e.message}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InspectionRepo", "Error listing inspections: ${e.message}")
        }

        // Sort by date (newest first)
        emit(inspections.sortedByDescending { it.inspectionDate })
    }.flowOn(Dispatchers.IO)

    /**
     * Get an inspection by name with improved error handling
     */
    suspend fun getInspection(inspectionName: String): Inspection? = withContext(Dispatchers.IO) {
        try {
            val fileName = sanitizeFileName(inspectionName)
            val file = File(inspectionsDir, "$fileName.json")

            if (!file.exists() || !file.isFile) {
                Log.d("InspectionRepo", "Inspection file does not exist: $fileName.json")
                return@withContext null
            }

            if (!file.canRead()) {
                Log.e("InspectionRepo", "Cannot read inspection file: $fileName.json")
                return@withContext null
            }

            try {
                val json = file.readText()
                if (json.isBlank()) {
                    Log.e("InspectionRepo", "Inspection file is empty: $fileName.json")
                    return@withContext null
                }

                val inspection = gson.fromJson(json, Inspection::class.java)
                return@withContext inspection
            } catch (e: Exception) {
                Log.e("InspectionRepo", "Error parsing inspection file $fileName.json: ${e.message}", e)
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("InspectionRepo", "Error getting inspection: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Save or update an inspection with improved error handling
     */
    suspend fun saveInspection(inspection: Inspection): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ensure the directory exists
            if (!inspectionsDir.exists()) {
                val dirCreated = inspectionsDir.mkdirs()
                if (!dirCreated) {
                    Log.e("InspectionRepo", "Failed to create inspections directory")
                    return@withContext false
                }
            }

            // Update the inspection date to current time
            val updatedInspection = inspection.copy(
                inspectionDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date())
            )

            // Generate file name and check if it's valid
            val fileName = sanitizeFileName(inspection.inspectionName)
            if (fileName.isBlank()) {
                Log.e("InspectionRepo", "Invalid inspection name: ${inspection.inspectionName}")
                return@withContext false
            }

            // Create the file
            val file = File(inspectionsDir, "$fileName.json")

            // Convert inspection to JSON
            val json = gson.toJson(updatedInspection)
            if (json.isBlank()) {
                Log.e("InspectionRepo", "Failed to serialize inspection to JSON")
                return@withContext false
            }

            // Use atomic file operations for safer writing
            try {
                // First write to a temporary file
                val tempFile = File(inspectionsDir, "$fileName.tmp")
                tempFile.writeText(json)

                // If successful, replace the original file
                if (file.exists()) {
                    file.delete()
                }

                val renamed = tempFile.renameTo(file)
                if (!renamed) {
                    // If rename fails, try a direct copy
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }

                Log.d("InspectionRepo", "Successfully saved inspection: ${inspection.inspectionName}")
                return@withContext true
            } catch (e: Exception) {
                Log.e("InspectionRepo", "Error writing inspection file: ${e.message}", e)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("InspectionRepo", "Error saving inspection: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Update a response for a specific question in an inspection
     * With improved error handling and consistent section key format
     */
    suspend fun updateResponse(
        inspectionName: String,
        sectionId: String,
        questionNumber: String,
        answer: String,
        comments: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get existing inspection or create new one
            val existingInspection = getInspection(inspectionName)
            if (existingInspection == null) {
                Log.e("InspectionRepo", "Cannot update response - inspection not found: $inspectionName")
                return@withContext false
            }

            // Get the section name consistently
            val sectionName = getSectionName(sectionId)

            // Always use the format "sectionId: sectionName" for consistency
            val sectionKey = "$sectionId: $sectionName"

            // Get the question text
            val questionText = getQuestionText(sectionId, questionNumber)

            // Create a mutable copy of the responses map
            val updatedResponses = existingInspection.responses.toMutableMap()

            // Get current responses for this section using the consistent key format
            val sectionResponses = updatedResponses[sectionKey]?.toMutableList() ?: mutableListOf()

            // Find existing response or add new one
            val existingIndex = sectionResponses.indexOfFirst { it.questionNumber == questionNumber }

            // Create the response object
            val questionResponse = QuestionResponse(
                questionNumber = questionNumber,
                answer = answer,
                comments = comments,
                questionText = questionText
            )

            if (existingIndex >= 0) {
                // Update existing response
                sectionResponses[existingIndex] = questionResponse
            } else {
                // Add new response
                sectionResponses.add(questionResponse)
            }

            // Update the responses map with the updated section responses
            updatedResponses[sectionKey] = sectionResponses

            // Remove old-format key to avoid duplicates (if it exists)
            if (updatedResponses.containsKey(sectionId) && sectionId != sectionKey) {
                updatedResponses.remove(sectionId)
            }

            // Special handling for vessel name (Q 1.1) and inspector name (Q 1.22)
            var vesselName = existingInspection.vessel
            var inspectorName = existingInspection.inspector

            // If this is the vessel name question (1.1), update the vessel field
            if (sectionId == "1" && questionNumber == "1.1" && answer.isNotBlank()) {
                vesselName = answer
                Log.d("InspectionRepo", "Updating vessel name to: '$answer'")
            }

            // If this is the inspector name question (1.22), update the inspector field
            if (sectionId == "1" && questionNumber == "1.22" && answer.isNotBlank()) {
                inspectorName = answer
                Log.d("InspectionRepo", "Updating inspector name to: '$answer'")
            }

            // Create updated inspection with new responses and possibly updated vessel/inspector
            val updatedInspection = existingInspection.copy(
                vessel = vesselName,
                inspector = inspectorName,
                responses = updatedResponses,
                inspectionDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date())
            )

            // Save the updated inspection
            val success = saveInspection(updatedInspection)

            if (success) {
                Log.d("InspectionRepo", "Successfully saved response for $inspectionName, section $sectionKey, question $questionNumber")

                // Explicitly log the vessel name after saving for debugging
                if (sectionId == "1" && questionNumber == "1.1") {
                    Log.d("InspectionRepo", "After save - Vessel name is now: '${updatedInspection.vessel}'")

                    // Verify the saved inspection by reading it back
                    val savedInspection = getInspection(inspectionName)
                    Log.d("InspectionRepo", "Verification - Vessel name in saved inspection: '${savedInspection?.vessel ?: "null"}'")
                }
            } else {
                Log.e("InspectionRepo", "Failed to save response for $inspectionName, section $sectionKey, question $questionNumber")
            }

            return@withContext success
        } catch (e: Exception) {
            Log.e("InspectionRepo", "Error updating response: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Delete an inspection
     */
    suspend fun deleteInspection(inspectionName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(inspectionsDir, "${sanitizeFileName(inspectionName)}.json")

            if (file.exists()) {
                return@withContext file.delete()
            }

            return@withContext false
        } catch (e: Exception) {
            Log.e("InspectionRepo", "Error deleting inspection: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Upload inspection JSON file to server (simplified focus on upload.php)
     */
    suspend fun uploadInspection(inspectionName: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val inspection = getInspection(inspectionName) ?: return@withContext Result.failure(
                    Exception("Inspection not found")
                )

                // Generate file content and name
                val jsonData = gson.toJson(inspection)
                val fileName =
                    "${sanitizeFileName(inspectionName)}_${System.currentTimeMillis()}.json"

                // Target the upload.php script specifically
                val uploadUrl = "http://nkdevelopment.net/app_sire_questions/upload.php"

                Log.d("InspectionRepo", "Uploading to: $uploadUrl")

                // Create connection
                val url = URL(uploadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Content-Type", "application/json")

                // Add filename as a parameter
                val urlWithParams = "$uploadUrl?filename=$fileName"
                val paramConnection = URL(urlWithParams).openConnection() as HttpURLConnection
                paramConnection.requestMethod = "POST"
                paramConnection.doOutput = true
                paramConnection.doInput = true  // Make sure we can read the response
                paramConnection.connectTimeout = 15000
                paramConnection.readTimeout = 15000
                paramConnection.setRequestProperty("Content-Type", "application/json")

                // Write data
                OutputStreamWriter(paramConnection.outputStream).use { writer ->
                    writer.write(jsonData)
                    writer.flush()
                }

                // Get response code
                val responseCode = paramConnection.responseCode
                Log.d("InspectionRepo", "Upload response code: $responseCode")

                // Read response body (for both success and error)
                val responseBody = if (responseCode == HttpURLConnection.HTTP_OK) {
                    paramConnection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    paramConnection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "No response"
                }

                Log.d("InspectionRepo", "Upload response: $responseBody")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Upload was successful
                    val uploadedUrl = "http://nkdevelopment.net/app_sire_questions/$fileName"
                    Log.d("InspectionRepo", "Successfully uploaded inspection: $uploadedUrl")
                    return@withContext Result.success(uploadedUrl)
                } else {
                    val error = "Server returned code: $responseCode. Response: $responseBody"
                    Log.e("InspectionRepo", "Upload failed: $error")
                    return@withContext Result.failure(Exception(error))
                }
            } catch (e: Exception) {
                Log.e("InspectionRepo", "Error uploading inspection: ${e.message}", e)
                return@withContext Result.failure(e)
            }
        }

    /**
     * Test if the server is reachable
     */
    private suspend fun testServerConnection(serverUrl: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                Log.d("InspectionRepo", "Server test response code: $responseCode")

                // Any response means the server is reachable
                return@withContext Result.success("Server responded with code: $responseCode")
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

    /**
     * Upload method 1: Using multipart form data
     */
    private suspend fun uploadWithMultipart(
        serverUrl: String,
        file: File,
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(serverUrl).openConnection() as HttpURLConnection
            val boundary = "*****${System.currentTimeMillis()}*****"

            connection.requestMethod = "POST"
            connection.doInput = true
            connection.doOutput = true
            connection.useCaches = false
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            connection.outputStream.use { outputStream ->
                val writer = OutputStreamWriter(outputStream)

                // Start multipart form data
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"uploaded_file\"; filename=\"$fileName\"\r\n")
                writer.append("Content-Type: application/json\r\n\r\n")
                writer.flush()

                // Write file content
                file.inputStream().use { fileInput ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                outputStream.flush()

                // End multipart form data
                writer.append("\r\n")
                writer.append("--$boundary--\r\n")
                writer.flush()
            }

            // Get response
            val responseCode = connection.responseCode
            Log.d("InspectionRepo", "Multipart upload response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return@withContext Result.success("$serverUrl$fileName")
            } else {
                val errorMessage = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "No error details"
                } catch (e: Exception) {
                    "Unable to read error details"
                }

                return@withContext Result.failure(Exception("Server returned code: $responseCode. Details: $errorMessage"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Upload method 2: Direct POST of JSON data
     */
    private suspend fun uploadWithDirectPost(
        serverUrl: String,
        jsonData: String,
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(serverUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Content-Type", "application/json")

            // Add filename parameter in URL if possible
            val completeUrl = if (serverUrl.contains("?")) {
                "$serverUrl&filename=$fileName"
            } else {
                "$serverUrl?filename=$fileName"
            }

            // Write data
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonData)
                writer.flush()
            }

            // Get response
            val responseCode = connection.responseCode
            Log.d("InspectionRepo", "Direct POST response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return@withContext Result.success("$serverUrl$fileName")
            } else {
                val errorMessage = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "No error details"
                } catch (e: Exception) {
                    "Unable to read error details"
                }

                return@withContext Result.failure(Exception("Server returned code: $responseCode. Details: $errorMessage"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Calculate completion percentage of an inspection
     */
    suspend fun calculateCompletionPercentage(inspection: Inspection): Float =
        withContext(Dispatchers.IO) {
            try {
                var totalQuestions = 0
                var answeredQuestions = 0

                // For each section in the app
                SectionData.sections.forEach { section ->
                    // Load the questions for this section
                    val sectionId = section.id.toString()
                    val questions = loadQuestionsForSection(sectionId)

                    if (questions.isNotEmpty()) {
                        // Exclude questions that have "N/A" as the only possible answer
                        val mandatoryQuestions = questions.filter {
                            it.possible_answers?.size != 1 || it.possible_answers[0] != "N/A"
                        }

                        totalQuestions += mandatoryQuestions.size

                        // Check responses for this section - handle section key with name included
                        val sectionKey = "$sectionId: ${section.title}"
                        val responsesWithName = inspection.responses[sectionKey] ?: emptyList()
                        val responsesWithoutName = inspection.responses[sectionId] ?: emptyList()
                        val responses =
                            if (responsesWithName.isNotEmpty()) responsesWithName else responsesWithoutName

                        // Count answered questions in this section
                        mandatoryQuestions.forEach { question ->
                            val response =
                                responses.find { it.questionNumber == question.question_number }
                            if (response != null && response.answer.isNotBlank()) {
                                answeredQuestions++
                            }
                        }
                    }
                }

                if (totalQuestions == 0) return@withContext 0f

                val completionRate = answeredQuestions.toFloat() / totalQuestions.toFloat()
                Log.d(
                    "InspectionRepo",
                    "Completion calculation for ${inspection.inspectionName}: $answeredQuestions/$totalQuestions = $completionRate"
                )

                return@withContext completionRate
            } catch (e: Exception) {
                Log.e("InspectionRepo", "Error calculating completion: ${e.message}")
                return@withContext 0f
            }
        }

    /**
     * Calculate completion percentage for a specific section
     */
    suspend fun calculateSectionCompletionPercentage(
        inspection: Inspection,
        sectionId: String
    ): Float = withContext(Dispatchers.IO) {
        try {
            val questions = loadQuestionsForSection(sectionId)

            if (questions.isEmpty()) return@withContext 0f

            // Exclude questions that have "N/A" as the only possible answer
            val mandatoryQuestions = questions.filter {
                it.possible_answers?.size != 1 || it.possible_answers[0] != "N/A"
            }

            if (mandatoryQuestions.isEmpty()) return@withContext 1f // All questions are N/A

            // Get section name
            val sectionName = getSectionName(sectionId)

            // Get responses for this section - check both formats (with and without section name)
            val sectionKeyWithName = "$sectionId: $sectionName"
            val responsesWithName = inspection.responses[sectionKeyWithName] ?: emptyList()
            val responsesWithoutName = inspection.responses[sectionId] ?: emptyList()
            val responses =
                if (responsesWithName.isNotEmpty()) responsesWithName else responsesWithoutName

            // Count answered questions
            var answeredCount = 0
            mandatoryQuestions.forEach { question ->
                val isAnswered = responses.any {
                    it.questionNumber == question.question_number && it.answer.isNotBlank()
                }
                if (isAnswered) answeredCount++
            }

            val completion = answeredCount.toFloat() / mandatoryQuestions.size.toFloat()
            Log.d(
                "InspectionRepo",
                "Section $sectionId completion: $answeredCount/${mandatoryQuestions.size} = $completion"
            )

            return@withContext completion
        } catch (e: Exception) {
            Log.e("InspectionRepo", "Error calculating section completion: ${e.message}")
            return@withContext 0f
        }
    }

    /**
     * Load questions for a specific section
     */
    suspend fun loadQuestionsForSection(sectionId: String): List<Question> =
        withContext(Dispatchers.IO) {
            try {
                val jsonString = context.assets.open("section_$sectionId.json").bufferedReader()
                    .use { it.readText() }
                val type = object : TypeToken<QuestionnaireData>() {}.type
                val data = Gson().fromJson<QuestionnaireData>(jsonString, type)
                return@withContext data.questions
            } catch (e: Exception) {
                Log.e(
                    "InspectionRepo",
                    "Error loading questions for section $sectionId: ${e.message}"
                )
                return@withContext emptyList()
            }
        }

    /**
     * Load an inspection from a JSON file
     */
    private fun loadInspectionFromFile(file: File): Inspection {
        val json = file.readText()
        return gson.fromJson(json, Inspection::class.java)
    }

    /**
     * Sanitize a file name to be safe for the file system - improved version
     */
    private fun sanitizeFileName(name: String): String {
        if (name.isBlank()) return "unnamed_inspection"

        val sanitized = name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .replace("\\s+".toRegex(), "_")
            .trim()

        return if (sanitized.isBlank()) "unnamed_inspection" else sanitized
    }

    /**
     * Get section name for a section ID
     */
    private fun getSectionName(sectionId: String): String {
        val id = sectionId.toIntOrNull() ?: return ""
        return SectionData.sections.find { it.id == id }?.title ?: ""
    }

    /**
     * Get question text for a specific question
     */
    private suspend fun getQuestionText(sectionId: String, questionNumber: String): String {
        try {
            val questions = loadQuestionsForSection(sectionId)
            val question = questions.find { it.question_number == questionNumber }
            return question?.question_text ?: ""
        } catch (e: Exception) {
            Log.e("InspectionRepo", "Error getting question text: ${e.message}")
            return ""
        }
    }

    /**
     * Updates vessel name and other key metadata from responses
     */
    suspend fun updateInspectionMetadata(inspectionName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val inspection = getInspection(inspectionName) ?: return@withContext false

                // Get section name
                val sectionName = getSectionName("1")

                // Try both key formats
                val sectionKeyWithName = "1: $sectionName"
                val sectionKeyWithoutName = "1"

                // Get responses with both key formats
                val sectionOneResponsesWithName =
                    inspection.responses[sectionKeyWithName] ?: emptyList()
                val sectionOneResponsesWithoutName =
                    inspection.responses[sectionKeyWithoutName] ?: emptyList()

                // Use responses with name if available, otherwise use without name
                val sectionOneResponses = if (sectionOneResponsesWithName.isNotEmpty())
                    sectionOneResponsesWithName else sectionOneResponsesWithoutName

                // Find vessel name (Q 1.1) and inspector name (Q 1.22) responses
                val vesselNameResponse = sectionOneResponses.find { it.questionNumber == "1.1" }
                val inspectorNameResponse = sectionOneResponses.find { it.questionNumber == "1.22" }

                // Check if we need to update
                val needsUpdate =
                    (vesselNameResponse != null && vesselNameResponse.answer.isNotBlank() &&
                            vesselNameResponse.answer != inspection.vessel) ||
                            (inspectorNameResponse != null && inspectorNameResponse.answer.isNotBlank() &&
                                    inspectorNameResponse.answer != inspection.inspector)

                if (needsUpdate) {
                    // Get new values or keep current if not available
                    val newVesselName = vesselNameResponse?.answer ?: inspection.vessel
                    val newInspectorName = inspectorNameResponse?.answer ?: inspection.inspector

                    Log.d(
                        "InspectionRepo",
                        "Updating metadata - vessel: '$newVesselName', inspector: '$newInspectorName'"
                    )

                    // Create updated inspection object with new metadata
                    val updatedInspection = inspection.copy(
                        vessel = newVesselName,
                        inspector = newInspectorName
                    )

                    // Save the updated inspection
                    val result = saveInspection(updatedInspection)
                    Log.d(
                        "InspectionRepo",
                        "Updated inspection metadata: vessel=${updatedInspection.vessel}, inspector=${updatedInspection.inspector}"
                    )
                    return@withContext result
                }

                return@withContext true // No update needed
            } catch (e: Exception) {
                Log.e("InspectionRepo", "Error updating inspection metadata: ${e.message}")
                return@withContext false
            }
        }

    /**
     * Function to debug the vessel name issue
     */
    suspend fun debugInspectionVesselName(inspectionName: String) = withContext(Dispatchers.IO) {
        try {
            val inspection = getInspection(inspectionName)
            if (inspection == null) {
                Log.e("DEBUG", "Inspection not found: $inspectionName")
                return@withContext
            }

            Log.d("DEBUG", "=== Inspection Debug ===")
            Log.d("DEBUG", "Name: ${inspection.inspectionName}")
            Log.d("DEBUG", "Vessel: '${inspection.vessel}'")
            Log.d("DEBUG", "Inspector: '${inspection.inspector}'")
            Log.d("DEBUG", "Response sections: ${inspection.responses.keys}")

            // Check if section 1 exists in any format
            val sectionName = getSectionName("1")
            val sectionKeyWithName = "1: $sectionName"

            Log.d("DEBUG", "Looking for sections: '$sectionKeyWithName' or '1'")

            val responsesWithName = inspection.responses[sectionKeyWithName]
            val responsesWithoutName = inspection.responses["1"]

            if (responsesWithName != null) {
                Log.d("DEBUG", "Found responses with section name")
                val vesselNameResponse = responsesWithName.find { it.questionNumber == "1.1" }
                Log.d("DEBUG", "Vessel name response: $vesselNameResponse")
            } else {
                Log.d("DEBUG", "No responses found with section name")
            }

            if (responsesWithoutName != null) {
                Log.d("DEBUG", "Found responses without section name")
                val vesselNameResponse = responsesWithoutName.find { it.questionNumber == "1.1" }
                Log.d("DEBUG", "Vessel name response: $vesselNameResponse")
            } else {
                Log.d("DEBUG", "No responses found without section name")
            }

            Log.d("DEBUG", "=== End Debug ===")
        } catch (e: Exception) {
            Log.e("DEBUG", "Error debugging inspection: ${e.message}")
        }
    }
}