package nkdevelopment.net.sire_questions

import android.app.Application
import android.util.Log

/**
 * Application class for initializing app-wide components
 */
class InspectionApp : Application() {

    // Lazy initialization of repository to ensure context is available
    val repository by lazy {
        InspectionRepository(this).also {
            Log.d("InspectionApp", "Repository initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("InspectionApp", "Application initialized")
    }
}