package com.example.ui.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.ai.GeminiIntelligenceService
import com.example.contacts.ContactsAndCallLogManager
import com.example.data.local.DialDatabase
import com.example.data.model.ActiveCallSession
import com.example.data.model.AudioRouteType
import com.example.data.model.CallDirection
import com.example.data.model.CallFilter
import com.example.data.model.CallLogEntry
import com.example.data.model.ContactItem
import com.example.data.model.DialCallState
import com.example.data.model.MainNavSection
import com.example.data.model.NewContactFormData
import com.example.data.model.PostCallSummary
import com.example.data.model.SpeedDialSlot
import com.example.data.repository.DialRepository
import com.example.telecom.CallStateRepository
import com.example.telecom.TelecomIntegrationManager
import com.example.ui.theme.DialThemePreference
import com.example.util.AudioSynthesizerAndPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppSettingsState(
    val themePreference: DialThemePreference = DialThemePreference.DARK,
    val hapticFeedbackEnabled: Boolean = true,
    val dialPadSoundsEnabled: Boolean = true,
    val incomingVibrationEnabled: Boolean = true,
    val defaultSimId: String = "",
    val alwaysAskSimWhenDual: Boolean = false,
    val autoSpeakerOnOutgoing: Boolean = false,
    val confirmBeforeEndCall: Boolean = false
)

class DialViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val REFRESH_COOLDOWN_MS = 1200L
        private const val REMINDER_CHANNEL_ID = "dial_callback_reminders_v2"
    }

    private val db = DialDatabase.getDatabase(application)
    val telecomIntegration = TelecomIntegrationManager(application)
    val contactsIntegration = ContactsAndCallLogManager(application)
    val repository = DialRepository(
        context = application,
        database = db,
        dao = db.dialDao(),
        telecomManager = telecomIntegration,
        contactsManager = contactsIntegration
    )
    private val geminiService = GeminiIntelligenceService()

    private val _isDefaultDialer = MutableStateFlow(telecomIntegration.isDefaultDialer())
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    private var hasDismissedDefaultOnboarding = false
    private val _shouldShowDefaultDialerOnboarding = MutableStateFlow(false)
    val shouldShowDefaultDialerOnboarding: StateFlow<Boolean> =
        _shouldShowDefaultDialerOnboarding.asStateFlow()

    private val _currentSection = MutableStateFlow(MainNavSection.KEYPAD)
    val currentSection: StateFlow<MainNavSection> = _currentSection.asStateFlow()

    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    private val settingsPrefs =
        application.getSharedPreferences("dial_nothing_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _canUndoCallLogDelete = MutableStateFlow(false)
    val canUndoCallLogDelete: StateFlow<Boolean> = _canUndoCallLogDelete.asStateFlow()
    private var recentlyDeletedCallLogs: List<CallLogEntry> = emptyList()
    private var bannerAutoDismissJob: Job? = null

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _selectedContact = MutableStateFlow<ContactItem?>(null)
    val selectedContact: StateFlow<ContactItem?> = _selectedContact.asStateFlow()

    private val _selectedCallLog = MutableStateFlow<CallLogEntry?>(null)
    val selectedCallLog: StateFlow<CallLogEntry?> = _selectedCallLog.asStateFlow()

    val activeCallSession: StateFlow<ActiveCallSession?> = CallStateRepository.activeCallSession
    val postCallSummary: StateFlow<PostCallSummary?> = CallStateRepository.postCallSummary

    private val _pendingSimSelectionNumber = MutableStateFlow<String?>(null)
    val pendingSimSelectionNumber: StateFlow<String?> = _pendingSimSelectionNumber.asStateFlow()

    private val _pendingPermissionCallNumber = MutableStateFlow<String?>(null)
    val pendingPermissionCallNumber: StateFlow<String?> = _pendingPermissionCallNumber.asStateFlow()

    private val _pendingAddContactForm = MutableStateFlow<NewContactFormData?>(null)
    val pendingAddContactForm: StateFlow<NewContactFormData?> = _pendingAddContactForm.asStateFlow()

    private val _callFilter = MutableStateFlow(CallFilter.ALL)
    val callFilter: StateFlow<CallFilter> = _callFilter.asStateFlow()

    private val _selectedRecentsIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRecentsIds: StateFlow<Set<Long>> = _selectedRecentsIds.asStateFlow()

    private val _contactSearchQuery = MutableStateFlow("")
    val contactSearchQuery: StateFlow<String> = _contactSearchQuery.asStateFlow()

    private val _deviceContacts = MutableStateFlow<List<ContactItem>>(emptyList())

    private val _isAiBusy = MutableStateFlow(false)
    val isAiBusy: StateFlow<Boolean> = _isAiBusy.asStateFlow()

    private val _aiGeneratedQuickReply = MutableStateFlow<String?>(null)
    val aiGeneratedQuickReply: StateFlow<String?> = _aiGeneratedQuickReply.asStateFlow()

    private val _aiErrorMessage = MutableStateFlow<String?>(null)
    val aiErrorMessage: StateFlow<String?> = _aiErrorMessage.asStateFlow()

    private var lastRefreshElapsedMs: Long = 0L
    private var refreshJob: Job? = null

    val allContacts: StateFlow<List<ContactItem>> = _deviceContacts.asStateFlow()

    val contactsList: StateFlow<List<ContactItem>> = combine(
        _deviceContacts,
        _contactSearchQuery
    ) { list, query ->
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) {
            list
        } else {
            list.filter { item ->
                item.name.lowercase().contains(clean) ||
                    item.phoneNumbers.any { it.lowercase().contains(clean) } ||
                    item.company.lowercase().contains(clean)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val keypadMatchingContacts: StateFlow<List<ContactItem>> = combine(
        _deviceContacts,
        _dialedNumber
    ) { contacts, dialed ->
        val clean = dialed.trim()
        if (clean.length < 2) {
            emptyList()
        } else {
            val digitsOnly = clean.filter { it.isDigit() }
            contacts.filter { item ->
                item.phoneNumbers.any { num ->
                    num.contains(clean) ||
                        (digitsOnly.isNotEmpty() && num.filter { it.isDigit() }.contains(digitsOnly))
                }
            }.take(5)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callHistoryList: StateFlow<List<CallLogEntry>> = combine(
        repository.persistedCallLogsFlow,
        _callFilter
    ) { logs, filter ->
        when (filter) {
            CallFilter.ALL -> logs
            CallFilter.MISSED -> logs.filter {
                it.callType == com.example.data.model.CallRecordType.MISSED ||
                    it.callType == com.example.data.model.CallRecordType.REJECTED
            }
            CallFilter.INCOMING -> logs.filter {
                it.callType == com.example.data.model.CallRecordType.INCOMING
            }
            CallFilter.OUTGOING -> logs.filter {
                it.callType == com.example.data.model.CallRecordType.OUTGOING
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val speedDialSlots: StateFlow<List<SpeedDialSlot>> = repository.speedDialFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedNumbers: StateFlow<List<String>> = repository.blockedNumbersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        CallStateRepository.contactNameResolver = { number ->
            findContactNameForNumber(number)
        }
        syncOnboardingVisibility()
        refreshDeviceData(force = true)

        viewModelScope.launch {
            repository.cleanupLegacyStorageArtifacts()
        }

        viewModelScope.launch {
            CallStateRepository.completedCallEvents.collect { completed ->
                repository.recordRealCallEvent(
                    phoneNumber = completed.phoneNumber,
                    contactName = completed.contactName,
                    callType = completed.callType,
                    timestamp = completed.timestamp,
                    durationSeconds = completed.durationSeconds,
                    simLabel = completed.simLabel
                )
                _currentSection.value = MainNavSection.RECENTS
            }
        }
    }

    private fun syncOnboardingVisibility() {
        val isDef = telecomIntegration.isDefaultDialer()
        _isDefaultDialer.value = isDef
        _shouldShowDefaultDialerOnboarding.value =
            !isDef && !hasDismissedDefaultOnboarding && telecomIntegration.isRoleDialerAvailable()
    }

    fun refreshDefaultDialerStatus() {
        syncOnboardingVisibility()
    }

    fun onDefaultDialerRoleResult(granted: Boolean) {
        syncOnboardingVisibility()
        if (granted) {
            showBanner("DIAL set as default phone app")
        }
    }

    fun dismissDefaultDialerOnboardingMaybeLater() {
        hasDismissedDefaultOnboarding = true
        _shouldShowDefaultDialerOnboarding.value = false
    }

    fun reopenDefaultDialerOnboarding() {
        hasDismissedDefaultOnboarding = false
        syncOnboardingVisibility()
    }

    fun refreshDeviceData(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRefreshElapsedMs < REFRESH_COOLDOWN_MS) return
        lastRefreshElapsedMs = now

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val contacts = contactsIntegration.queryDeviceContacts()
            _deviceContacts.value = contacts
            repository.syncProviderCallLogsToRoom(contacts)
            CallStateRepository.refreshActiveCallContactInfo()
        }
    }

    fun selectMainSection(section: MainNavSection) {
        _currentSection.value = section
    }

    fun showBanner(message: String, allowUndo: Boolean = false) {
        bannerAutoDismissJob?.cancel()
        _canUndoCallLogDelete.value = allowUndo
        _statusBannerMessage.value = message
        bannerAutoDismissJob = viewModelScope.launch {
            delay(4000L)
            _statusBannerMessage.value = null
            _canUndoCallLogDelete.value = false
        }
    }

    fun dismissBanner() {
        bannerAutoDismissJob?.cancel()
        _statusBannerMessage.value = null
        _canUndoCallLogDelete.value = false
    }

    fun onKeypadDigitTap(digit: Char) {
        if (_dialedNumber.value.length < 24) {
            _dialedNumber.value += digit
        }
        AudioSynthesizerAndPlayer.playDialpadDtmfTone(
            digit = digit,
            enabled = _settings.value.dialPadSoundsEnabled
        )
    }

    fun onKeypadDigitLongPress(digit: Char) {
        when (digit) {
            '0' -> {
                _dialedNumber.value += "+"
            }
            in '2'..'9' -> {
                val d = digit.digitToInt()
                val slot = speedDialSlots.value.firstOrNull { it.digit == d }
                if (slot != null && slot.phoneNumber.isNotBlank()) {
                    initiateCall(slot.phoneNumber)
                } else {
                    showBanner("Speed dial $d is not assigned")
                }
            }
            else -> onKeypadDigitTap(digit)
        }
    }

    fun onBackspaceTap() {
        val current = _dialedNumber.value
        if (current.isNotEmpty()) {
            _dialedNumber.value = current.dropLast(1)
        }
    }

    fun onBackspaceLongPress() {
        _dialedNumber.value = ""
    }

    fun onPasteNumber(rawText: String) {
        val cleaned = rawText.filter { it.isDigit() || it in setOf('+', '*', '#') }
        if (cleaned.isNotEmpty()) {
            _dialedNumber.value = cleaned.take(24)
        }
    }

    fun setDialedNumberFromIntent(number: String) {
        val cleaned = number.trim()
        if (cleaned.isNotEmpty()) {
            _dialedNumber.value = cleaned
            _currentSection.value = MainNavSection.KEYPAD
        }
    }

    fun initiateCall(targetNumber: String = _dialedNumber.value, simId: String? = null) {
        val clean = targetNumber.trim()
        if (clean.isEmpty()) {
            val lastOutgoing = callHistoryList.value.firstOrNull()?.phoneNumber.orEmpty()
            if (lastOutgoing.isNotEmpty()) {
                _dialedNumber.value = lastOutgoing
            } else {
                showBanner("Enter a phone number to call")
            }
            return
        }

        val sims = telecomIntegration.getAvailableSimAccounts()
        if (simId == null && sims.size > 1 && _settings.value.alwaysAskSimWhenDual) {
            _pendingSimSelectionNumber.value = clean
            return
        }

        _pendingSimSelectionNumber.value = null
        placeNativeOutgoingCall(clean, simId ?: _settings.value.defaultSimId.ifBlank { null })
    }

    fun dismissSimSelection() {
        _pendingSimSelectionNumber.value = null
    }

    fun onCallPermissionResult(granted: Boolean) {
        val pending = _pendingPermissionCallNumber.value
        _pendingPermissionCallNumber.value = null
        if (granted && !pending.isNullOrBlank()) {
            placeNativeOutgoingCall(pending, null)
        }
    }

    private fun placeNativeOutgoingCall(phoneNumber: String, simId: String?) {
        val result = telecomIntegration.placeOutgoingCall(phoneNumber, simId)
        result.onSuccess { placedDirectly ->
            _dialedNumber.value = ""
            if (!placedDirectly) {
                showBanner("Opened system dialer for $phoneNumber")
            }
        }.onFailure { err ->
            showBanner(err.message ?: "Unable to place call")
        }
    }

    fun answerIncomingCall() {
        CallStateRepository.answerIncomingCall()
    }

    fun silenceIncomingCallRinger() {
        CallStateRepository.silenceRinger()
    }

    fun remindMeLaterOnIncomingCall(minutes: Int = 15) {
        val session = activeCallSession.value
        val number = session?.phoneNumber.orEmpty().ifBlank { "Unknown Caller" }
        val name = session?.contactName.orEmpty().ifBlank { number }

        CallStateRepository.rejectOrEndCall()
        showBanner("Callback reminder scheduled for $name in $minutes min")

        viewModelScope.launch {
            delay((minutes.coerceAtLeast(1) * 1000L).coerceAtMost(3000L))
            postHighPriorityCallbackReminderNotification(name, number, minutes)
        }
    }

    private fun postHighPriorityCallbackReminderNotification(
        callerName: String,
        phoneNumber: String,
        minutes: Int
    ) {
        val context = getApplication<Application>()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Callback Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority callback reminders from incoming calls"
            }
            nm.createNotificationChannel(channel)
        }

        val callBackIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val callBackPi = PendingIntent.getActivity(
            context,
            phoneNumber.hashCode(),
            callBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phoneNumber)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val smsPi = PendingIntent.getActivity(
            context,
            phoneNumber.hashCode() + 1,
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Call back $callerName")
            .setContentText("Reminder ($minutes min) • $phoneNumber")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(callBackPi)
            .addAction(android.R.drawable.sym_action_call, "Call Back", callBackPi)
            .addAction(android.R.drawable.sym_action_email, "Message", smsPi)
            .build()

        runCatching {
            nm.notify(5500 + (phoneNumber.hashCode() and 0x7FF), notification)
        }
    }

    fun rejectIncomingCall() {
        CallStateRepository.rejectOrEndCall()
    }

    fun toggleIncomingAnswerMode() {
        CallStateRepository.toggleIncomingAnswerMode()
    }

    fun setIncomingTouchInteraction(isTouching: Boolean) {
        CallStateRepository.setIncomingTouchInteraction(isTouching)
    }

    fun minimizeActiveCallScreen() {
        CallStateRepository.setUiMinimized(true)
    }

    fun restoreActiveCallScreen() {
        CallStateRepository.setUiMinimized(false)
    }

    fun toggleInCallKeypad() {
        CallStateRepository.updateSessionState { session ->
            session.copy(isKeypadExpanded = !session.isKeypadExpanded)
        }
    }

    fun sendInCallDtmf(digit: Char) {
        AudioSynthesizerAndPlayer.playDialpadDtmfTone(digit, enabled = true)
        CallStateRepository.playDtmfTone(digit)
    }

    fun requestSwitchToVideoCall() {
        if (!CallStateRepository.requestVideoUpgrade()) {
            showBanner("Carrier video upgrade unavailable for this call")
        }
    }

    fun toggleMute() {
        val current = activeCallSession.value ?: return
        CallStateRepository.setMuted(!current.isMuted)
    }

    fun selectAudioRoute(route: AudioRouteType) {
        CallStateRepository.setAudioRoute(route)
    }

    fun cycleSpeakerRoute() {
        val current = activeCallSession.value ?: return
        val next = if (current.audioRoute == AudioRouteType.SPEAKER) {
            AudioRouteType.EARPIECE
        } else {
            AudioRouteType.SPEAKER
        }
        CallStateRepository.setAudioRoute(next)
    }

    fun toggleHoldCall() {
        CallStateRepository.toggleHold()
    }

    fun addSecondaryCall(phoneNumber: String, contactName: String) {
        val clean = phoneNumber.trim()
        if (clean.isEmpty()) return
        CallStateRepository.toggleHold()
        placeNativeOutgoingCall(clean, null)
    }

    fun swapActiveCalls() {
        CallStateRepository.swapActiveAndHeldCalls()
    }

    fun mergeCallsIntoConference() {
        CallStateRepository.mergeConferenceCall()
    }

    fun endActiveCall() {
        CallStateRepository.rejectOrEndCall()
        _currentSection.value = MainNavSection.RECENTS
    }

    fun cancelPostCallAutoReturn() {
        CallStateRepository.cancelPostCallAutoReturn()
    }

    fun dismissPostCallSummary() {
        CallStateRepository.dismissPostCallSummary()
        _currentSection.value = MainNavSection.RECENTS
    }

    fun sendQuickResponse(phoneNumber: String, message: String) {
        CallStateRepository.rejectOrEndCall()
        val (_, statusMsg) = telecomIntegration.sendQuickResponseSms(phoneNumber, message)
        showBanner(statusMsg)
    }

    fun generateAiSmartQuickReply(callerNameOrNumber: String, userContextPrompt: String) {
        if (_isAiBusy.value) return
        _isAiBusy.value = true
        _aiErrorMessage.value = null
        viewModelScope.launch {
            val res = geminiService.generateSmartQuickReply(callerNameOrNumber, userContextPrompt)
            _isAiBusy.value = false
            res.onSuccess { reply ->
                _aiGeneratedQuickReply.value = reply
            }.onFailure { err ->
                _aiErrorMessage.value = err.message ?: "Unable to draft AI quick reply"
            }
        }
    }

    fun clearAiQuickReply() {
        _aiGeneratedQuickReply.value = null
        _aiErrorMessage.value = null
    }

    fun openContactDetail(contact: ContactItem) {
        _selectedContact.value = contact
    }

    fun closeContactDetail() {
        _selectedContact.value = null
    }

    fun setContactSearchQuery(query: String) {
        _contactSearchQuery.value = query
    }

    fun toggleFavouriteContact(contact: ContactItem) {
        val updated = contact.copy(isStarred = !contact.isStarred)
        _selectedContact.value = updated
        _deviceContacts.value = _deviceContacts.value.map {
            if (it.id == contact.id) updated else it
        }
    }

    fun addNewContact(phoneNumber: String, suggestedName: String, email: String = "") {
        contactsIntegration.openAddContactIntent(phoneNumber, suggestedName)
    }

    fun openNewContactSheet(
        phoneNumber: String = "",
        suggestedName: String = "",
        email: String = ""
    ) {
        val parts = suggestedName.trim().split(" ", limit = 2)
        _pendingAddContactForm.value = NewContactFormData(
            firstName = parts.getOrNull(0).orEmpty(),
            lastName = parts.getOrNull(1).orEmpty(),
            phone = phoneNumber.trim(),
            email = email.trim()
        )
    }

    fun dismissNewContactSheet() {
        _pendingAddContactForm.value = null
    }

    fun saveStructuredContact(form: NewContactFormData) {
        _pendingAddContactForm.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val res = contactsIntegration.insertStructuredContact(form)
            res.onSuccess {
                refreshDeviceData(force = true)
                showBanner("Saved ${form.fullName.ifBlank { form.phone }} to contacts")
            }.onFailure {
                contactsIntegration.openAddContactIntent(form.phone, form.fullName)
            }
        }
    }

    fun openCallLogDetail(entry: CallLogEntry) {
        _selectedCallLog.value = entry
    }

    fun closeCallLogDetail() {
        _selectedCallLog.value = null
    }

    fun setCallFilter(filter: CallFilter) {
        _callFilter.value = filter
    }

    fun toggleCallLogSelection(id: Long) {
        val current = _selectedRecentsIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedRecentsIds.value = current
    }

    fun clearCallLogSelection() {
        _selectedRecentsIds.value = emptySet()
    }

    fun deleteSelectedCallLogs() {
        val ids = _selectedRecentsIds.value.toList()
        if (ids.isEmpty()) return
        val currentLogs = callHistoryList.value
        recentlyDeletedCallLogs = currentLogs.filter { ids.contains(it.id) }
        _selectedRecentsIds.value = emptySet()
        viewModelScope.launch {
            repository.deleteCallLogs(ids)
            showBanner("Deleted ${ids.size} call log entries", allowUndo = true)
        }
    }

    fun deleteSingleCallLog(entry: CallLogEntry) {
        recentlyDeletedCallLogs = listOf(entry)
        viewModelScope.launch {
            repository.deleteCallLogs(listOf(entry.id))
            showBanner("Deleted call log entry", allowUndo = true)
        }
    }

    fun undoDeleteCallLogs() {
        val toRestore = recentlyDeletedCallLogs
        recentlyDeletedCallLogs = emptyList()
        dismissBanner()
        if (toRestore.isEmpty()) return
        viewModelScope.launch {
            toRestore.forEach { entry ->
                repository.recordRealCallEvent(
                    phoneNumber = entry.phoneNumber,
                    contactName = entry.contactName,
                    callType = entry.callType,
                    timestamp = entry.timestamp,
                    durationSeconds = entry.duration,
                    simLabel = entry.simLabel
                )
            }
            showBanner("Restored call log entries")
        }
    }

    fun blockPhoneNumber(number: String) {
        viewModelScope.launch {
            repository.addBlockedNumber(number)
            showBanner("Blocked $number")
        }
    }

    fun unblockPhoneNumber(number: String) {
        viewModelScope.launch {
            repository.removeBlockedNumber(number)
            showBanner("Unblocked $number")
        }
    }

    fun assignSpeedDial(digit: Int, contactName: String, phoneNumber: String) {
        viewModelScope.launch {
            repository.saveSpeedDial(digit, contactName, phoneNumber)
            showBanner("Speed dial $digit updated")
        }
    }

    private fun loadSettings(): AppSettingsState {
        val themeStr = settingsPrefs.getString("theme_pref", DialThemePreference.DARK.name)
            ?: DialThemePreference.DARK.name
        val theme = runCatching { DialThemePreference.valueOf(themeStr) }
            .getOrDefault(DialThemePreference.DARK)
        return AppSettingsState(
            themePreference = theme,
            hapticFeedbackEnabled = settingsPrefs.getBoolean("haptic_enabled", true),
            dialPadSoundsEnabled = settingsPrefs.getBoolean("dtmf_sounds", true),
            incomingVibrationEnabled = settingsPrefs.getBoolean("incoming_vibration", true),
            defaultSimId = settingsPrefs.getString("default_sim_id", "").orEmpty(),
            alwaysAskSimWhenDual = settingsPrefs.getBoolean("always_ask_sim", false),
            autoSpeakerOnOutgoing = settingsPrefs.getBoolean("auto_speaker_outgoing", false),
            confirmBeforeEndCall = settingsPrefs.getBoolean("confirm_before_end", false)
        )
    }

    private fun saveSettings(state: AppSettingsState) {
        settingsPrefs.edit()
            .putString("theme_pref", state.themePreference.name)
            .putBoolean("haptic_enabled", state.hapticFeedbackEnabled)
            .putBoolean("dtmf_sounds", state.dialPadSoundsEnabled)
            .putBoolean("incoming_vibration", state.incomingVibrationEnabled)
            .putString("default_sim_id", state.defaultSimId)
            .putBoolean("always_ask_sim", state.alwaysAskSimWhenDual)
            .putBoolean("auto_speaker_outgoing", state.autoSpeakerOnOutgoing)
            .putBoolean("confirm_before_end", state.confirmBeforeEndCall)
            .apply()
    }

    fun resetSettingsToDefault() {
        val defaults = AppSettingsState()
        _settings.value = defaults
        saveSettings(defaults)
        showBanner("Settings reset to default")
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        val updated = transform(_settings.value)
        _settings.value = updated
        saveSettings(updated)
    }

    fun findContactNameForNumber(phoneNumber: String): String {
        return contactsIntegration.lookupContactNameForNumber(
            phoneNumber = phoneNumber,
            cachedContacts = _deviceContacts.value
        )
    }
}
