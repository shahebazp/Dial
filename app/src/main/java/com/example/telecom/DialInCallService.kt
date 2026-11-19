package com.example.telecom

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.example.MainActivity
import com.example.data.model.ActiveCallSession
import com.example.data.model.AudioRouteType
import com.example.data.model.CallDirection
import com.example.data.model.CallRecordType
import com.example.data.model.DialCallState
import com.example.data.model.PostCallSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CompletedCallRecordEvent(
    val phoneNumber: String,
    val contactName: String,
    val callType: CallRecordType,
    val timestamp: Long,
    val durationSeconds: Long,
    val simLabel: String
)

object CallStateRepository {
    private const val TAG = "CallStateRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var activeService: DialInCallService? = null
    var alertController: IncomingCallAlertController? = null
    var primaryTelecomCall: Call? = null
    var secondaryTelecomCall: Call? = null

    @Volatile
    var isAppInForeground: Boolean = false
        private set

    private val _activeCallSession = MutableStateFlow<ActiveCallSession?>(null)
    val activeCallSession: StateFlow<ActiveCallSession?> = _activeCallSession.asStateFlow()

    private val _postCallSummary = MutableStateFlow<PostCallSummary?>(null)
    val postCallSummary: StateFlow<PostCallSummary?> = _postCallSummary.asStateFlow()

    private val _completedCallEvents = MutableSharedFlow<CompletedCallRecordEvent>(extraBufferCapacity = 8)
    val completedCallEvents: SharedFlow<CompletedCallRecordEvent> = _completedCallEvents.asSharedFlow()

    private var durationTimerJob: Job? = null
    private var postCallDismissJob: Job? = null
    private var handledDisconnectCallId: String? = null

    var contactNameResolver: ((String) -> String)? = null

    @Volatile
    private var lastPowerButtonRejectedCallId: String? = null

    fun isDeviceActivelyInUse(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isInteractive = pm?.isInteractive == true
        val isKeyguardLocked = km?.isKeyguardLocked == true
        return isInteractive && !isKeyguardLocked
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    private fun resolveCallerName(
        phoneNumber: String,
        details: Call.Details?,
        context: Context?,
        existingSession: ActiveCallSession?,
        forceLookup: Boolean
    ): String {
        val telecomDisplayName = details?.callerDisplayName?.trim().orEmpty()
        val contactDisplayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            details?.contactDisplayName?.trim().orEmpty()
        } else {
            ""
        }
        if (contactDisplayName.isNotEmpty()) return contactDisplayName
        if (telecomDisplayName.isNotEmpty()) return telecomDisplayName

        if (!forceLookup && existingSession != null && existingSession.contactName.isNotBlank()) {
            return existingSession.contactName
        }

        val resolved = contactNameResolver?.invoke(phoneNumber)?.trim().orEmpty()
        if (resolved.isNotEmpty()) return resolved

        return existingSession?.contactName.orEmpty()
    }

    fun onTelecomCallUpdated(
        call: Call?,
        audioState: CallAudioState? = activeService?.callAudioState,
        context: Context? = activeService
    ) {
        if (call == null) {
            stopDurationTimer()
            alertController?.stopIncomingCallAlerts()
            alertController?.syncInCallProximityWakeLock(null)
            alertController?.cancelCallNotification(activeService)
            _activeCallSession.value = null
            return
        }

        val details = call.details
        val handleUri = details?.handle
        val rawNumber = handleUri?.schemeSpecificPart?.trim().orEmpty().ifEmpty { "Unknown" }
        val callId = System.identityHashCode(call).toString()
        val existing = _activeCallSession.value
        val isNewCall = existing == null || existing.callId != callId

        val callState = when (call.state) {
            Call.STATE_RINGING -> DialCallState.INCOMING
            Call.STATE_CONNECTING -> DialCallState.CONNECTING
            Call.STATE_DIALING -> DialCallState.DIALLING
            Call.STATE_ACTIVE -> DialCallState.ACTIVE
            Call.STATE_HOLDING -> DialCallState.HOLDING
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> DialCallState.DISCONNECTED
            else -> DialCallState.CONNECTING
        }

        val direction = when {
            !isNewCall -> existing!!.direction
            call.state == Call.STATE_RINGING -> CallDirection.INCOMING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                details?.callDirection == Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
            else -> CallDirection.OUTGOING
        }

        val resolvedName = resolveCallerName(
            phoneNumber = rawNumber,
            details = details,
            context = context,
            existingSession = if (isNewCall) null else existing,
            forceLookup = isNewCall
        )

        val currentAudioRoute = when (audioState?.route) {
            CallAudioState.ROUTE_SPEAKER -> AudioRouteType.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> AudioRouteType.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioRouteType.WIRED_HEADSET
            else -> AudioRouteType.EARPIECE
        }

        val supportedRouteMask = audioState?.supportedRouteMask ?: 0
        val availableRoutes = buildList {
            add(AudioRouteType.EARPIECE)
            add(AudioRouteType.SPEAKER)
            if ((supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0) {
                add(AudioRouteType.BLUETOOTH)
            }
            if ((supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
                add(AudioRouteType.WIRED_HEADSET)
            }
        }

        val initialPocketMode = if (isNewCall && callState == DialCallState.INCOMING) {
            alertController?.shouldUseSlideToAnswer() == true
        } else {
            existing?.isPocketModeLatched == true
        }

        val useSlide = when {
            callState != DialCallState.INCOMING -> false
            isNewCall -> initialPocketMode
            else -> existing?.useSlideToAnswer ?: initialPocketMode
        }

        val isDeviceInUse = if (isNewCall && callState == DialCallState.INCOMING && context != null) {
            isDeviceActivelyInUse(context)
        } else {
            existing?.isIncomingBannerMode == true
        }

        val connectionTimeMs = when {
            callState == DialCallState.ACTIVE && existing?.connectionTimestampMs != null ->
                existing.connectionTimestampMs
            callState == DialCallState.ACTIVE ->
                details?.connectTimeMillis?.takeIf { it > 0L } ?: System.currentTimeMillis()
            else -> existing?.connectionTimestampMs
        }

        val elapsedSec = if (connectionTimeMs != null && callState in setOf(DialCallState.ACTIVE, DialCallState.HOLDING)) {
            ((System.currentTimeMillis() - connectionTimeMs) / 1000L).coerceAtLeast(0L)
        } else {
            existing?.durationSeconds ?: 0L
        }

        val secondary = secondaryTelecomCall?.takeIf { it != call }
        val secondaryNum = secondary?.details?.handle?.schemeSpecificPart.orEmpty()
        val secondaryName = if (secondaryNum.isNotEmpty()) {
            contactNameResolver?.invoke(secondaryNum).orEmpty().ifEmpty { secondaryNum }
        } else {
            ""
        }

        val updatedSession = ActiveCallSession(
            callId = callId,
            contactName = resolvedName,
            phoneNumber = rawNumber,
            direction = direction,
            state = callState,
            simLabel = existing?.simLabel ?: "SIM 1",
            isMuted = audioState?.isMuted ?: (existing?.isMuted ?: false),
            audioRoute = currentAudioRoute,
            availableRoutes = availableRoutes,
            connectionTimestampMs = connectionTimeMs,
            disconnectionTimestampMs = if (callState == DialCallState.DISCONNECTED) System.currentTimeMillis() else null,
            durationSeconds = elapsedSec,
            isKeypadExpanded = if (callState == DialCallState.INCOMING) false else (existing?.isKeypadExpanded ?: false),
            dtmfHistory = if (isNewCall) "" else existing?.dtmfHistory.orEmpty(),
            hasSecondaryCall = secondary != null,
            secondaryCallName = secondaryName,
            secondaryCallNumber = secondaryNum,
            isConference = call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true,
            canSwitchToVideo = (details?.callCapabilities ?: 0) and Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL != 0,
            isVideoCall = VideoProfile.isVideo(details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY),
            isRingerSilenced = existing?.isRingerSilenced ?: false,
            useSlideToAnswer = useSlide,
            isPocketModeLatched = initialPocketMode,
            isManualModeOverride = existing?.isManualModeOverride ?: false,
            isUserTouchingIncomingControls = existing?.isUserTouchingIncomingControls ?: false,
            isUiMinimized = if (isNewCall) false else (existing?.isUiMinimized ?: false),
            isIncomingBannerMode = false,
            isProximityScreenOffActive = alertController?.isProximityCurrentlyNear() == true
        )

        if (callState == DialCallState.DISCONNECTED) {
            handleCallDisconnected(updatedSession, details?.disconnectCause)
            return
        }

        _postCallSummary.value = null
        _activeCallSession.value = updatedSession

        if (callState == DialCallState.INCOMING) {
            alertController?.startIncomingCallAlerts()
        } else {
            alertController?.stopIncomingCallAlerts(resetNotificationSignature = false)
        }

        alertController?.syncInCallProximityWakeLock(updatedSession)
        alertController?.updateCallSessionNotification(
            session = updatedSession,
            service = activeService,
            isDeviceInUseForIncoming = isDeviceInUse
        )

        if (callState in setOf(DialCallState.ACTIVE, DialCallState.HOLDING)) {
            startDurationTimerIfNeeded()
        } else {
            stopDurationTimer()
        }
    }

    fun refreshActiveCallContactInfo() {
        val current = _activeCallSession.value ?: return
        val resolved = contactNameResolver?.invoke(current.phoneNumber)?.trim().orEmpty()
        if (resolved.isNotEmpty() && resolved != current.contactName) {
            _activeCallSession.update { it?.copy(contactName = resolved) }
        }
    }

    private fun startDurationTimerIfNeeded() {
        if (durationTimerJob?.isActive == true) return
        durationTimerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val s = _activeCallSession.value ?: break
                val connMs = s.connectionTimestampMs
                if (connMs != null && s.state in setOf(DialCallState.ACTIVE, DialCallState.HOLDING)) {
                    val sec = ((System.currentTimeMillis() - connMs) / 1000L).coerceAtLeast(0L)
                    _activeCallSession.update { it?.copy(durationSeconds = sec) }
                }
            }
        }
    }

    private fun stopDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = null
    }

    private fun handleCallDisconnected(
        disconnectedSession: ActiveCallSession,
        disconnectCause: DisconnectCause?
    ) {
        if (handledDisconnectCallId == disconnectedSession.callId) return
        handledDisconnectCallId = disconnectedSession.callId
        lastPowerButtonRejectedCallId = null

        stopDurationTimer()
        alertController?.stopIncomingCallAlerts()
        alertController?.syncInCallProximityWakeLock(null)
        alertController?.cancelCallNotification(activeService)

        val wasConnected = disconnectedSession.connectionTimestampMs != null || disconnectedSession.durationSeconds > 0L
        val recordType = when {
            disconnectedSession.direction == CallDirection.OUTGOING -> CallRecordType.OUTGOING
            wasConnected -> CallRecordType.INCOMING
            disconnectCause?.code == DisconnectCause.REJECTED -> CallRecordType.REJECTED
            else -> CallRecordType.MISSED
        }

        scope.launch {
            _completedCallEvents.emit(
                CompletedCallRecordEvent(
                    phoneNumber = disconnectedSession.phoneNumber,
                    contactName = disconnectedSession.contactName,
                    callType = recordType,
                    timestamp = System.currentTimeMillis(),
                    durationSeconds = disconnectedSession.durationSeconds,
                    simLabel = disconnectedSession.simLabel
                )
            )
        }

        _activeCallSession.value = null
        _postCallSummary.value = null
    }

    fun cancelPostCallAutoReturn() {
        postCallDismissJob?.cancel()
        postCallDismissJob = null
    }

    fun dismissPostCallSummary() {
        cancelPostCallAutoReturn()
        _postCallSummary.value = null
    }

    fun updateSessionState(transform: (ActiveCallSession) -> ActiveCallSession) {
        _activeCallSession.update { current -> current?.let(transform) }
    }

    internal fun setSessionForTesting(session: ActiveCallSession?) {
        _activeCallSession.value = session
    }

    fun answerIncomingCall(): Boolean {
        val call = primaryTelecomCall
        alertController?.stopIncomingCallAlerts(resetNotificationSignature = false)
        return if (call != null) {
            try {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                _activeCallSession.update {
                    it?.copy(
                        state = DialCallState.ACTIVE,
                        connectionTimestampMs = it.connectionTimestampMs ?: System.currentTimeMillis(),
                        isUiMinimized = false,
                        isIncomingBannerMode = false
                    )
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to answer Telecom call", e)
                false
            }
        } else {
            val current = _activeCallSession.value ?: return false
            _activeCallSession.value = current.copy(
                state = DialCallState.ACTIVE,
                connectionTimestampMs = System.currentTimeMillis(),
                isUiMinimized = false,
                isIncomingBannerMode = false
            )
            true
        }
    }

    fun silenceRinger(): Boolean {
        alertController?.stopIncomingCallAlerts(resetNotificationSignature = false)
        try {
            val tm = activeService?.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            tm?.silenceRinger()
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager.silenceRinger failed", e)
        }
        _activeCallSession.update { it?.copy(isRingerSilenced = true) }
        return true
    }

    fun toggleIncomingAnswerMode() {
        _activeCallSession.update { current ->
            current?.copy(
                useSlideToAnswer = !current.useSlideToAnswer,
                isManualModeOverride = true
            )
        }
    }

    fun setIncomingTouchInteraction(isTouching: Boolean) {
        _activeCallSession.update { current ->
            current?.copy(isUserTouchingIncomingControls = isTouching)
        }
    }

    fun onSensorPocketStateChanged(isInPocket: Boolean) {
        // Per bug fix #2: Initial detection latches the mode for the duration of the incoming call;
        // subsequent proximity changes during the same call must not switch UI mode.
    }

    fun onInCallProximityNearChanged(isNear: Boolean) {
        _activeCallSession.update { current ->
            current?.copy(isProximityScreenOffActive = isNear)
        }
    }

    fun setUiMinimized(minimized: Boolean) {
        _activeCallSession.update { current ->
            current?.copy(
                isUiMinimized = minimized,
                isIncomingBannerMode = if (!minimized) false else current.isIncomingBannerMode
            )
        }
    }

    fun onPowerLockButtonPressed(): Boolean {
        val current = _activeCallSession.value ?: return false
        if (current.state != DialCallState.INCOMING || !current.useSlideToAnswer) {
            return false
        }
        if (lastPowerButtonRejectedCallId == current.callId) {
            return false
        }
        lastPowerButtonRejectedCallId = current.callId
        Log.i(TAG, "Power/lock button pressed in Pocket Mode — rejecting incoming call ${current.callId}")
        return rejectOrEndCall()
    }

    fun rejectOrEndCall(): Boolean {
        alertController?.stopIncomingCallAlerts()
        val call = primaryTelecomCall
        if (call != null) {
            return try {
                if (call.state == Call.STATE_RINGING) {
                    call.reject(false, null)
                } else {
                    call.disconnect()
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reject or disconnect call", e)
                false
            }
        }
        val current = _activeCallSession.value ?: return false
        handleCallDisconnected(
            current.copy(state = DialCallState.DISCONNECTED),
            DisconnectCause(DisconnectCause.LOCAL)
        )
        return true
    }

    fun requestVideoUpgrade(): Boolean {
        val call = primaryTelecomCall ?: return false
        return try {
            val videoCall = call.videoCall ?: return false
            videoCall.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_BIDIRECTIONAL))
            _activeCallSession.update { it?.copy(isVideoCall = true) }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Video upgrade failed", e)
            false
        }
    }

    fun setMuted(muted: Boolean): Boolean {
        return try {
            activeService?.setMuted(muted)
            val am = activeService?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.isMicrophoneMute = muted
            _activeCallSession.update { it?.copy(isMuted = muted) }
            val updated = _activeCallSession.value
            if (updated != null) {
                alertController?.updateCallSessionNotification(updated, activeService)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "setMuted failed", e)
            false
        }
    }

    fun toggleMuteFromNotification(): Boolean {
        val currentMuted = _activeCallSession.value?.isMuted ?: false
        return setMuted(!currentMuted)
    }

    fun setAudioRoute(route: AudioRouteType): Boolean {
        val telecomRoute = when (route) {
            AudioRouteType.EARPIECE -> CallAudioState.ROUTE_EARPIECE
            AudioRouteType.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            AudioRouteType.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            AudioRouteType.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        }
        return try {
            activeService?.setAudioRoute(telecomRoute)
            _activeCallSession.update { it?.copy(audioRoute = route) }
            val updated = _activeCallSession.value
            if (updated != null) {
                alertController?.syncInCallProximityWakeLock(updated)
                alertController?.updateCallSessionNotification(updated, activeService)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "setAudioRoute failed", e)
            false
        }
    }

    fun cycleAudioRouteFromNotification(): Boolean {
        val current = _activeCallSession.value ?: return false
        val routes = current.availableRoutes.ifEmpty {
            listOf(AudioRouteType.EARPIECE, AudioRouteType.SPEAKER)
        }
        val nextIndex = (routes.indexOf(current.audioRoute) + 1) % routes.size
        return setAudioRoute(routes[nextIndex])
    }

    fun playDtmfTone(digit: Char): Boolean {
        val call = primaryTelecomCall
        return try {
            call?.playDtmfTone(digit)
            scope.launch {
                delay(140L)
                runCatching { call?.stopDtmfTone() }
            }
            _activeCallSession.update { current ->
                current?.copy(dtmfHistory = current.dtmfHistory + digit)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "playDtmfTone failed", e)
            false
        }
    }

    fun toggleHold(): Boolean {
        val call = primaryTelecomCall ?: return false
        return try {
            if (call.state == Call.STATE_HOLDING) {
                call.unhold()
                _activeCallSession.update { it?.copy(state = DialCallState.ACTIVE) }
            } else {
                call.hold()
                _activeCallSession.update { it?.copy(state = DialCallState.HOLDING) }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "toggleHold failed", e)
            false
        }
    }

    fun swapActiveAndHeldCalls(): Boolean {
        val primary = primaryTelecomCall ?: return false
        val secondary = secondaryTelecomCall ?: return false
        return try {
            if (primary.state == Call.STATE_ACTIVE) {
                primary.hold()
            }
            if (secondary.state == Call.STATE_HOLDING) {
                secondary.unhold()
            }
            primaryTelecomCall = secondary
            secondaryTelecomCall = primary
            onTelecomCallUpdated(primaryTelecomCall)
            true
        } catch (e: Exception) {
            Log.w(TAG, "swapActiveAndHeldCalls failed", e)
            false
        }
    }

    fun mergeConferenceCall(): Boolean {
        val primary = primaryTelecomCall ?: return false
        val secondary = secondaryTelecomCall ?: return false
        return try {
            primary.conference(secondary)
            _activeCallSession.update { it?.copy(isConference = true) }
            true
        } catch (e: Exception) {
            Log.w(TAG, "mergeConferenceCall failed", e)
            false
        }
    }
}

class DialInCallService : InCallService() {

    companion object {
        private const val TAG = "DialInCallService"
    }

    private var localAlertController: IncomingCallAlertController? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallStateRepository.onTelecomCallUpdated(
                call = CallStateRepository.primaryTelecomCall ?: call,
                audioState = callAudioState,
                context = this@DialInCallService
            )
        }

        override fun onDetailsChanged(call: Call, details: Call.Details?) {
            CallStateRepository.onTelecomCallUpdated(
                call = CallStateRepository.primaryTelecomCall ?: call,
                audioState = callAudioState,
                context = this@DialInCallService
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallStateRepository.activeService = this
        val controller = IncomingCallAlertController(applicationContext)
        localAlertController = controller
        CallStateRepository.alertController = controller
    }

    override fun onDestroy() {
        CallStateRepository.alertController?.cancelCallNotification(this)
        localAlertController?.release()
        localAlertController = null
        if (CallStateRepository.activeService === this) {
            CallStateRepository.activeService = null
            CallStateRepository.alertController = null
        }
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        call.registerCallback(callCallback)
        if (CallStateRepository.primaryTelecomCall == null) {
            CallStateRepository.primaryTelecomCall = call
        } else if (CallStateRepository.primaryTelecomCall !== call) {
            CallStateRepository.secondaryTelecomCall = CallStateRepository.primaryTelecomCall
            CallStateRepository.primaryTelecomCall = call
        }

        CallStateRepository.onTelecomCallUpdated(
            call = CallStateRepository.primaryTelecomCall,
            audioState = callAudioState,
            context = this
        )

        if (!CallStateRepository.isAppInForeground) {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CallNotificationActionReceiver.EXTRA_RETURN_TO_CALL, true)
            }
            runCatching { startActivity(openIntent) }
        }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callCallback)
        if (CallStateRepository.primaryTelecomCall === call) {
            CallStateRepository.primaryTelecomCall = CallStateRepository.secondaryTelecomCall
            CallStateRepository.secondaryTelecomCall = null
        } else if (CallStateRepository.secondaryTelecomCall === call) {
            CallStateRepository.secondaryTelecomCall = null
        }
        CallStateRepository.onTelecomCallUpdated(
            call = CallStateRepository.primaryTelecomCall,
            audioState = callAudioState,
            context = this
        )
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        val activeCall = CallStateRepository.primaryTelecomCall ?: return
        CallStateRepository.onTelecomCallUpdated(
            call = activeCall,
            audioState = audioState,
            context = this
        )
    }
}
