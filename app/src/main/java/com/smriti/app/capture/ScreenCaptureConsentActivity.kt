package com.smriti.app.capture

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class ScreenCaptureConsentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) {
            Log.e(ScreenCaptureService.TAG, "MediaProjectionManager not available")
            finish()
            return
        }

        val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                ScreenCaptureService.start(this, result.resultCode, result.data!!)
            } else {
                Log.i(ScreenCaptureService.TAG, "Screen capture consent denied or cancelled (resultCode=${result.resultCode})")
            }
            finish()
        }

        if (savedInstanceState == null) {
            launcher.launch(projectionManager.createScreenCaptureIntent())
        }
    }
}
