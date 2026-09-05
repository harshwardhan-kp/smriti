package com.smriti.app.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object BubbleLauncher {
    fun isRunning(): Boolean = BubbleService.isRunning()

    /**
     * Returns true when the bubble was started, false when the user was sent to a
     * permission screen instead and nothing was started yet.
     */
    fun start(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(overlayIntent)
            return false
        }

        if (!ScreenCaptureService.isArmed()) {
            val consentIntent = Intent(context, ScreenCaptureConsentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(consentIntent)
        }

        BubbleService.start(context)
        return true
    }

    fun stop(context: Context) {
        BubbleService.stop(context)
    }
}
