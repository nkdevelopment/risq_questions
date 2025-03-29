package nkdevelopment.net.sire_questions

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility function to export section questions and answers to a PDF file
 */
suspend fun exportToPdf(
    context: Context,
    sectionId: Int,
    questions: List<Question>,
    answers: Map<String, String>,
    comments: Map<String, String>,
    inspectionName: String?,
    onPdfCreated: (String) -> Unit
): String = withContext(Dispatchers.IO) {

    // PDF document setup
    val document = PdfDocument()
    val pageWidth = 595 // A4 width in points
    val pageHeight = 842 // A4 height in points
    val margin = 50

    // Paints for text styling
    val titlePaint = Paint().apply {
        color = Color.rgb(0, 0, 0)
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val headerPaint = Paint().apply {
        color = Color.rgb(0, 0, 0)
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val normalPaint = Paint().apply {
        color = Color.rgb(0, 0, 0)
        textSize = 12f
        typeface = Typeface.DEFAULT
    }

    val smallPaint = Paint().apply {
        color = Color.rgb(100, 100, 100)
        textSize = 10f
        typeface = Typeface.DEFAULT
    }

    val italicPaint = Paint().apply {
        color = Color.rgb(80, 80, 80)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    }

    // Section information
    val section = SectionData.sections.find { it.id == sectionId }
    val sectionTitle = section?.title ?: "Section $sectionId"

    // Create first page
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    var page = document.startPage(pageInfo)
    var canvas = page.canvas

    // Variables to track position and pages
    var y = margin + 30f
    var pageNum = 1

    // Draw header
    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    val currentDate = dateFormat.format(Date())

    canvas.drawText("Vessel Inspection Report", margin.toFloat(), y, titlePaint)
    y += 24

    canvas.drawText("Section $sectionId: $sectionTitle", margin.toFloat(), y, headerPaint)
    y += 20

    if (!inspectionName.isNullOrBlank()) {
        canvas.drawText("Inspection: $inspectionName", margin.toFloat(), y, headerPaint)
        y += 20
    }

    canvas.drawText("Generated: $currentDate", margin.toFloat(), y, normalPaint)
    y += 36

    // Draw divider
    canvas.drawLine(margin.toFloat(), y - 10, (pageWidth - margin).toFloat(), y - 10, normalPaint)

    // Function to check if we need a new page
    fun checkPageBreak(requiredSpace: Float): Boolean {
        if (y + requiredSpace > pageHeight - margin) {
            // Finish current page
            document.finishPage(page)

            // Start new page
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = margin + 30f

            // Draw header on new page
            canvas.drawText("Section $sectionId: $sectionTitle - Page $pageNum", margin.toFloat(), y, headerPaint)
            y += 36

            return true
        }
        return false
    }

    // Draw questions and answers
    for (question in questions) {
        // Calculate required space for this question
        val hasComment = comments[question.question_number]?.isNotBlank() == true
        val estimatedSpace = if (hasComment) 200f else 160f  // More space if there's a comment

        // Check if we need a new page for this question
        checkPageBreak(estimatedSpace)

        // Question number and text
        canvas.drawText("${question.question_number}", margin.toFloat(), y, headerPaint)
        y += 20

        // Wrap question text if needed
        val questionTextLines = wrapText(question.question_text, normalPaint, pageWidth - (2 * margin))
        for (line in questionTextLines) {
            canvas.drawText(line, margin.toFloat(), y, normalPaint)
            y += 20
        }

        // Answer section
        y += 10
        canvas.drawText("Answer:", margin.toFloat() + 20, y, headerPaint)
        y += 20

        val answer = answers[question.question_number] ?: "Not answered"
        canvas.drawText(answer, margin.toFloat() + 20, y, normalPaint)
        y += 30

        // Comment section if there is a comment
        val comment = comments[question.question_number]
        if (!comment.isNullOrBlank()) {
            checkPageBreak(80f)  // Check if we need a new page for the comment

            canvas.drawText("Comment:", margin.toFloat() + 20, y, headerPaint)
            y += 20

            // Wrap comment text
            val commentTextLines = wrapText(comment, italicPaint, pageWidth - (2 * margin) - 20)
            for (line in commentTextLines) {
                canvas.drawText(line, margin.toFloat() + 20, y, italicPaint)
                y += 20
            }

            y += 10
        }

        // Draw a separator line
        canvas.drawLine(margin.toFloat(), y - 10, (pageWidth - margin).toFloat(), y - 10, Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        })

        y += 20
    }

    // Finish the document
    document.finishPage(page)

    // Save the PDF file
    val fileName = "Inspection_Section${sectionId}_${System.currentTimeMillis()}.pdf"
    val filePath = "${context.getExternalFilesDir(null)}/$fileName"

    try {
        FileOutputStream(filePath).use { out ->
            document.writeTo(out)
        }
        document.close()
        onPdfCreated(filePath)
        return@withContext filePath
    } catch (e: Exception) {
        e.printStackTrace()
        document.close()
        return@withContext ""
    }
}

/**
 * Utility function to wrap text for PDF rendering
 */
private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
    val result = mutableListOf<String>()

    val words = text.split(" ")
    var currentLine = StringBuilder()

    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
        val testWidth = paint.measureText(testLine)

        if (testWidth <= maxWidth) {
            currentLine = StringBuilder(testLine)
        } else {
            // Line is full, add it to results
            if (currentLine.isNotEmpty()) {
                result.add(currentLine.toString())
            }
            currentLine = StringBuilder(word)
        }
    }

    // Add the last line if it's not empty
    if (currentLine.isNotEmpty()) {
        result.add(currentLine.toString())
    }

    return result
}