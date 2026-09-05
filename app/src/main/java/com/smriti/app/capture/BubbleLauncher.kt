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

        // Deliberately NOT asking for MediaProjection consent here.
        //
        // Two bubbles come up together and only one of them needs it. The mic bubble records a
        // voice note and never touches the screen; demanding screen-recording consent before it
        // will even appear puts Android's "Smriti will be able to access everything on your
        // screen, including audio, passwords and other sensitive information" sheet in front of
        // someone who only wanted to talk. That is a bad trade and it is also unnecessary:
        // BubbleService.startCapture already checks isArmed() and launches the consent activity
        // itself, the first time the capture bubble is actually held.
        //
        // So consent is asked for at the moment it is needed, by the feature that needs it.
        BubbleService.start(context)
        return true
    }

    fun stop(context: Context) {
        BubbleService.stop(context)
    }
}
