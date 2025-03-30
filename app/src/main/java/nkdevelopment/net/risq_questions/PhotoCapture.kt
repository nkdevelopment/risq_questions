package nkdevelopment.net.risq_questions

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class to handle photo capture functionality
 */
class PhotoCaptureUtil(private val activity: SectionActivity) {

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private var currentPhotoUri: Uri? = null
    private var currentQuestionNumber: String? = null
    private var photoResultCallback: ((String, Uri) -> Unit)? = null

    // Pending photo capture callback for when permission is granted
    private var pendingCaptureCallback: (() -> Unit)? = null

    /**
     * Initialize the photo capture functionality
     */
    fun initialize(callback: (String, Uri) -> Unit) {
        photoResultCallback = callback

        takePictureLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                currentPhotoUri?.let { uri ->
                    currentQuestionNumber?.let { questionNumber ->
                        photoResultCallback?.invoke(questionNumber, uri)
                    }
                }
            } else {
                Log.d("PhotoCaptureUtil", "Photo capture canceled or failed")
            }
        }

        // Register permission callback in the activity
        activity.setCameraPermissionResultCallback { isGranted ->
            if (isGranted) {
                pendingCaptureCallback?.invoke()
            } else {
                Toast.makeText(
                    activity,
                    "Cannot take photos without camera permission",
                    Toast.LENGTH_LONG
                ).show()
            }
            pendingCaptureCallback = null
        }
    }

    /**
     * Take a photo for a specific question
     */
    fun takePhoto(questionNumber: String) {
        currentQuestionNumber = questionNumber

        // Check for camera permission before proceeding
        checkCameraPermission {
            launchCamera()
        }
    }

    /**
     * Check if camera permission is granted, request if needed
     */
    private fun checkCameraPermission(callback: () -> Unit) {
        if (ContextCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted
            callback()
        } else {
            // Store callback for when permission is granted
            pendingCaptureCallback = callback

            // Request permission
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * Launch the camera intent
     */
    private fun launchCamera() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Log.e("PhotoCaptureUtil", "Error creating image file", ex)
            Toast.makeText(activity, "Error creating image file: ${ex.message}", Toast.LENGTH_SHORT).show()
            null
        }

        photoFile?.let {
            try {
                currentPhotoUri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.provider",
                    it
                )

                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)

                    // Add flags to grant URI permissions temporarily
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                // Log intent details for debugging
                Log.d("PhotoCaptureUtil", "Launching camera with URI: $currentPhotoUri")

                takePictureLauncher.launch(takePictureIntent)
            } catch (e: Exception) {
                Log.e("PhotoCaptureUtil", "Error launching camera: ${e.message}", e)
                Toast.makeText(activity, "Error launching camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Create a file to save the image
     */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name with timestamp
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        if (storageDir == null || !storageDir.exists()) {
            if (storageDir != null && !storageDir.mkdirs()) {
                throw IOException("Failed to create directory for pictures")
            }
        }

        return File.createTempFile(
            "INSPECTION_${timeStamp}_",  // prefix
            ".jpg",  // suffix
            storageDir  // directory
        ).apply {
            Log.d("PhotoCaptureUtil", "Photo file created at: $absolutePath")
        }
    }

    /**
     * Get all photos for a specific inspection
     */
    fun getInspectionPhotos(inspectionName: String): List<File> {
        val storageDir: File? = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val photosList = mutableListOf<File>()

        storageDir?.listFiles()?.forEach { file ->
            if (file.name.startsWith("INSPECTION_") && file.name.endsWith(".jpg")) {
                photosList.add(file)
            }
        }

        return photosList
    }
}