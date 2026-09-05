Waiting for the unit tests to complete...
Waiting for the build and tests to finish...
Waiting for the test rerun to complete...
### Summary of Changes

1. **`app/src/main/AndroidManifest.xml`**
   - Added `android:foregroundServiceType="specialUse"` to the `<service android:name=".capture.BubbleService">` declaration.
   - Added the required property child element `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Persistent capture overlay the user explicitly enabled" />` inside the `<service>` tag.
   - Left all existing permissions and the network-stripping rules (`tools:node="remove"`) untouched.

2. **`app/src/main/java/com/smriti/app/capture/BubbleService.kt`**
   - **Foreground Service Notification & Channel**:
     - Defined `CHANNEL_ID = "smriti_bubble"` and `NOTIFICATION_ID = 2002`.
     - Added `createNotificationChannel()` configuring channel id `"smriti_bubble"`, name `"Capture bubble"`, with `NotificationManager.IMPORTANCE_LOW` and badge disabled.
     - Added `createNotification()` using title `"Smriti capture bubble"`, text `"Long-press the bubble to capture and narrate"`, `R.mipmap.ic_launcher` as the small icon, `setOngoing(true)`, and a `PendingIntent` targeting [`MainActivity`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/MainActivity.kt).
   - **Lifecycle & API Guarding**:
     - Called `startForegroundServiceNotification()` in `onCreate()` before attaching the overlay view, guarding API levels with the 3-argument `startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` on API 29+ and the 2-argument overload on earlier versions.
     - Added `stopForegroundNotification()` that calls `stopForeground(STOP_FOREGROUND_REMOVE)` and explicitly cancels the notification via `NotificationManager.cancel(NOTIFICATION_ID)`.
     - Cleaned up the notification and foreground status upon `ACTION_STOP`, early exit when overlay permission is missing, overlay attachment failure, and `onDestroy()`.
   - **Sticky Restart & Overlay Attachment**:
     - Handled `intent == null` in [`onStartCommand`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleService.kt#L268-L288) (delivered on Android sticky restarts) by explicitly invoking `attachBubbleView()` and returning `START_STICKY`.
     - Preserved the `@Volatile private var bubbleAttached` guard to maintain attachment idempotence and avoid duplicate view addition.
   - **Service Starter**:
     - Updated [`BubbleService.start(context)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleService.kt#L69-L75) to use `ContextCompat.startForegroundService(context, intent)` instead of `context.startService(intent)`.

3. **`app/src/test/java/com/smriti/app/capture/BubbleServiceTest.kt`**
   - Added unit test asserting [`BubbleService.CHANNEL_ID`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleService.kt#L54) equals `"smriti_bubble"`.

4. **Verification**:
   - Ran `./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest` — build succeeded, `:app:assertNoNetworkPermission` verified 0 network leaks, and all unit tests passed.
