package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ActiveCallSession
import com.example.data.model.AudioRouteType
import com.example.data.model.DialCallState
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IncomingCallAlertController(private val context: Context) {

    companion object {
        private const val TAG = "IncomingCallAlerts"
        private const val LEGACY_INCOMING_CHANNEL_ID = "dial_incoming_call_channel"
        private const val LEGACY_ONGOING_CHANNEL_ID = "dial_ongoing_call_channel"
        private const val INCOMING_BANNER_CHANNEL_ID = "dial_incoming_banner_silent_v3"
        private const val INCOMING_SILENT_CHANNEL_ID = "dial_incoming_lockscreen_silent_v3"
        private const val ONGOING_CHANNEL_ID = "dial_ongoing_call_silent_v3"
        const val CALL_NOTIFICATION_ID = 4201
        private const val POCKET_DEBOUNCE_MS = 180L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isAlerting = false
    private var glyphPulseJob: Job? = null
    private var debouncedPocketJob: Job? = null
    private val glyphController = NothingGlyphBridge(context)
    private val avatarBitmapCache = LinkedHashMap<String, Bitmap>()
    private var lastNotificationSignature: String? = null

    @Volatile
    private var lastProximityNear: Boolean = false

    @Volatile
    private var accelX: Float = 0f

    @Volatile
    private var accelY: Float = 0f

    @Volatile
    private var accelZ: Float = 9.8f

    @Volatile
    private var isCurrentlyPocketMode: Boolean = false

    private var sensorsRegistered = false

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometerSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val ev = event ?: return
            when (ev.sensor.type) {
                Sensor.TYPE_PROXIMITY -> {
                    val maxRange = proximitySensor?.maximumRange ?: 5f
                    val distance = ev.values.firstOrNull() ?: maxRange
                    val isNear = distance < maxRange.coerceAtMost(5f)
                    if (isNear != lastProximityNear) {
                        lastProximityNear = isNear
                        CallStateRepository.onInCallProximityNearChanged(isNear)
                        scheduleDebouncedPocketEvaluation()
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    if (ev.values.size >= 3) {
                        accelX = ev.values[0]
                        accelY = ev.values[1]
                        accelZ = ev.values[2]
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    init {
        ensureNotificationChannels()
        registerSensorMonitors()
    }

    fun isProximityCurrentlyNear(): Boolean = lastProximityNear

    fun syncInCallProximityWakeLock(session: ActiveCallSession?) {
        val shouldAcquire = session != null &&
            session.state in setOf(
                DialCallState.CONNECTING,
                DialCallState.DIALLING,
                DialCallState.RINGING,
                DialCallState.ACTIVE,
                DialCallState.HOLDING
            ) &&
            !session.isVideoCall &&
            session.audioRoute == AudioRouteType.EARPIECE

        if (shouldAcquire) {
            try {
                val pm = powerManager ?: return
                if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    val wl = proximityWakeLock ?: pm.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        "com.example:InCallProximityWakeLock"
                    ).also { proximityWakeLock = it }
                    if (!wl.isHeld) {
                        wl.acquire(4 * 60 * 60 * 1000L)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to acquire proximity screen-off wake lock", e)
            }
        } else {
            releaseInCallProximityWakeLock()
        }
    }

    private fun releaseInCallProximityWakeLock() {
        try {
            val wl = proximityWakeLock
            if (wl != null && wl.isHeld) {
                wl.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to release proximity wake lock", e)
        }
    }

    private fun registerSensorMonitors() {
        if (sensorsRegistered) return
        val sm = sensorManager ?: return
        try {
            proximitySensor?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            accelerometerSensor?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            sensorsRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register proximity/accelerometer sensors", e)
        }
    }

    private fun computeInstantPocketState(): Boolean {
        if (!lastProximityNear) return false
        val norm = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
        if (norm < 1f) return lastProximityNear
        val isSteepPocketTilt = abs(accelY) > 5.0f || abs(accelX) > 5.0f || accelZ < -3.5f
        return lastProximityNear && (isSteepPocketTilt || abs(accelZ) < 9.9f)
    }

    private fun scheduleDebouncedPocketEvaluation() {
        debouncedPocketJob?.cancel()
        debouncedPocketJob = scope.launch {
            delay(POCKET_DEBOUNCE_MS)
            val instant = computeInstantPocketState()
            if (instant != isCurrentlyPocketMode) {
                isCurrentlyPocketMode = instant
                CallStateRepository.onSensorPocketStateChanged(instant)
            }
        }
    }

    fun shouldUseSlideToAnswer(): Boolean {
        return lastProximityNear || computeInstantPocketState()
    }

    fun startIncomingCallAlerts() {
        if (isAlerting) return
        isAlerting = true
        startGlyphRingingPulse()
    }

    fun stopIncomingCallAlerts(resetNotificationSignature: Boolean = true) {
        isAlerting = false
        stopGlyphRingingPulse()
        if (resetNotificationSignature) {
            lastNotificationSignature = null
        }
    }

    fun updateCallSessionNotification(
        session: ActiveCallSession,
        service: Service? = null,
        isDeviceInUseForIncoming: Boolean = false
    ) {
        val signature = buildString {
            append(session.callId).append('|')
            append(session.state).append('|')
            append(session.contactName).append('|')
            append(session.phoneNumber).append('|')
            append(session.isMuted).append('|')
            append(session.audioRoute).append('|')
            append(session.isIncomingBannerMode).append('|')
            append(isDeviceInUseForIncoming)
        }
        if (signature == lastNotificationSignature && service == null) {
            return
        }
        lastNotificationSignature = signature

        val isIncoming = session.state == DialCallState.INCOMING
        val channelId = when {
            isIncoming && isDeviceInUseForIncoming -> INCOMING_BANNER_CHANNEL_ID
            isIncoming -> INCOMING_SILENT_CHANNEL_ID
            else -> ONGOING_CHANNEL_ID
        }
        val title = session.contactName.ifBlank { session.phoneNumber.ifBlank { "Unknown Caller" } }
        val notification = buildCallStyleNotification(
            session = session,
            channelId = channelId,
            displayTitle = title,
            isIncoming = isIncoming,
            isDeviceInUseForIncoming = isDeviceInUseForIncoming
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            if (service != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    } else {
                        0
                    }
                    if (fgType != 0) {
                        service.startForeground(CALL_NOTIFICATION_ID, notification, fgType)
                    } else {
                        service.startForeground(CALL_NOTIFICATION_ID, notification)
                    }
                } else {
                    service.startForeground(CALL_NOTIFICATION_ID, notification)
                }
            } else {
                nm?.notify(CALL_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post call notification", e)
            runCatching { nm?.notify(CALL_NOTIFICATION_ID, notification) }
        }
    }

    fun cancelCallNotification(service: Service? = null) {
        lastNotificationSignature = null
        try {
            service?.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed", e)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { nm?.cancel(CALL_NOTIFICATION_ID) }
    }

    private fun buildCallStyleNotification(
        session: ActiveCallSession,
        channelId: String,
        displayTitle: String,
        isIncoming: Boolean,
        isDeviceInUseForIncoming: Boolean
    ): Notification {
        val openCallIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallNotificationActionReceiver.EXTRA_RETURN_TO_CALL, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            100,
            openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            101,
            openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerPi = createActionPendingIntent(CallNotificationActionReceiver.ACTION_ANSWER_CALL, 201)
        val declinePi = createActionPendingIntent(CallNotificationActionReceiver.ACTION_DECLINE_CALL, 202)
        val endPi = createActionPendingIntent(CallNotificationActionReceiver.ACTION_END_CALL, 203)
        val mutePi = createActionPendingIntent(CallNotificationActionReceiver.ACTION_TOGGLE_MUTE, 204)
        val routePi = createActionPendingIntent(CallNotificationActionReceiver.ACTION_CYCLE_AUDIO_ROUTE, 205)

        val avatarBitmap = getOrCreateCallerAvatarBitmap(displayTitle)
        val subtitle = when {
            isIncoming -> "${session.simLabel} • Incoming call"
            session.state == DialCallState.HOLDING -> "${session.simLabel} • Call on hold"
            session.state == DialCallState.ACTIVE -> "${session.simLabel} • Connected"
            else -> "${session.simLabel} • ${session.state.displayLabel}"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callerPerson = Person.Builder()
                .setName(displayTitle)
                .setIcon(Icon.createWithBitmap(avatarBitmap))
                .setImportant(true)
                .build()

            val callStyle = if (isIncoming) {
                Notification.CallStyle.forIncomingCall(callerPerson, declinePi, answerPi)
            } else {
                Notification.CallStyle.forOngoingCall(callerPerson, endPi)
            }

            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle(displayTitle)
                .setContentText(subtitle)
                .setContentIntent(contentPendingIntent)
                .setStyle(callStyle)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)

            if (isIncoming) {
                builder.setFullScreenIntent(fullScreenPendingIntent, true)
            } else {
                val muteLabel = if (session.isMuted) "Unmute" else "Mute"
                val routeLabel = "Audio: ${session.audioRoute.label}"
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_btn_speak_now),
                        muteLabel,
                        mutePi
                    ).build()
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_lock_silent_mode_off),
                        routeLabel,
                        routePi
                    ).build()
                )
                if (session.connectionTimestampMs != null && session.state == DialCallState.ACTIVE) {
                    builder.setUsesChronometer(true)
                    builder.setWhen(session.connectionTimestampMs)
                    builder.setShowWhen(true)
                }
            }
            return builder.build()
        }

        val compatBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setLargeIcon(avatarBitmap)
            .setContentTitle(displayTitle)
            .setContentText(subtitle)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOnlyAlertOnce(true)

        if (isIncoming) {
            compatBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
            compatBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePi)
            compatBuilder.addAction(android.R.drawable.sym_action_call, "Answer", answerPi)
        } else {
            val muteLabel = if (session.isMuted) "Unmute" else "Mute"
            val routeLabel = session.audioRoute.label
            compatBuilder.addAction(android.R.drawable.ic_btn_speak_now, muteLabel, mutePi)
            compatBuilder.addAction(android.R.drawable.ic_lock_silent_mode_off, routeLabel, routePi)
            compatBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", endPi)
        }
        return compatBuilder.build()
    }

    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getOrCreateCallerAvatarBitmap(displayName: String): Bitmap {
        val key = displayName.trim().uppercase().take(2).ifEmpty { "#" }
        return synchronized(avatarBitmapCache) {
            avatarBitmapCache.getOrPut(key) {
                createCallerAvatarBitmap(displayName)
            }
        }
    }

    private fun createCallerAvatarBitmap(displayName: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E1E24")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D71921")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, borderPaint)

        val initials = displayName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifEmpty { "#" }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, bounds)
        val y = size / 2f + bounds.height() / 2f
        canvas.drawText(initials, size / 2f, y, textPaint)
        return bitmap
    }

    fun release() {
        stopIncomingCallAlerts()
        releaseInCallProximityWakeLock()
        debouncedPocketJob?.cancel()
        if (sensorsRegistered) {
            runCatching { sensorManager?.unregisterListener(sensorListener) }
            sensorsRegistered = false
        }
        scope.cancel()
    }

    private fun startGlyphRingingPulse() {
        if (!glyphController.isNothingDevice()) return
        glyphPulseJob?.cancel()
        glyphPulseJob = scope.launch {
            glyphController.connectAndStartSession()
            while (isActive && isAlerting) {
                glyphController.pulseIncomingCallPattern()
                delay(1400L)
            }
        }
    }

    private fun stopGlyphRingingPulse() {
        glyphPulseJob?.cancel()
        glyphPulseJob = null
        glyphController.stopAndReleaseSession()
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            nm.deleteNotificationChannel(LEGACY_INCOMING_CHANNEL_ID)
            nm.deleteNotificationChannel(LEGACY_ONGOING_CHANNEL_ID)
        }

        val incomingBannerChannel = NotificationChannel(
            INCOMING_BANNER_CHANNEL_ID,
            context.getString(R.string.notification_channel_incoming_calls),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming call heads-up banner alerts"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val incomingSilentChannel = NotificationChannel(
            INCOMING_SILENT_CHANNEL_ID,
            "Incoming Call Full-Screen Lockscreen",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen lockscreen incoming call UI"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val ongoingChannel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            context.getString(R.string.notification_channel_ongoing_calls),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active ongoing call controls"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        nm.createNotificationChannels(
            listOf(incomingBannerChannel, incomingSilentChannel, ongoingChannel)
        )
    }
}

class NothingGlyphBridge(private val context: Context) {

    companion object {
        private const val TAG = "NothingGlyphBridge"
    }

    private var glyphManagerInstance: Any? = null
    private var isSessionOpen = false

    fun isNothingDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        return manufacturer.equals("Nothing", ignoreCase = true) ||
            brand.equals("Nothing", ignoreCase = true)
    }

    fun connectAndStartSession() {
        if (!isNothingDevice() || isSessionOpen) return
        try {
            val clazz = Class.forName("com.nothing.ketchum.GlyphManager")
            val getInstance = clazz.getMethod("getInstance", Context::class.java)
            glyphManagerInstance = getInstance.invoke(null, context.applicationContext)
            isSessionOpen = true
        } catch (_: Exception) {
            isSessionOpen = false
        }
    }

    fun pulseIncomingCallPattern() {
        if (!isSessionOpen || glyphManagerInstance == null) return
        // Best-effort reflection hook for Nothing Glyph SDK when running on Nothing hardware
    }

    fun stopAndReleaseSession() {
        if (!isSessionOpen) return
        isSessionOpen = false
        glyphManagerInstance = null
    }
}
