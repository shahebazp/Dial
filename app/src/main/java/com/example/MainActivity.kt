package com.example

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ActiveCallSession
import com.example.data.model.DialCallState
import com.example.data.model.MainNavSection
import com.example.telecom.CallNotificationActionReceiver
import com.example.telecom.CallStateRepository
import com.example.ui.screens.ActiveCallOverlayScreen
import com.example.ui.screens.ContactsDirectoryScreen
import com.example.ui.screens.DefaultDialerOnboardingOverlay
import com.example.ui.screens.DialSettingsScreen
import com.example.ui.screens.DialerKeypadScreen
import com.example.ui.screens.GoogleStyleAddContactSheet
import com.example.ui.screens.PostCallSummaryScreen
import com.example.ui.screens.RecentsCallHistoryScreen
import com.example.ui.theme.DialTheme
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceMonoFontFamily
import com.example.ui.viewmodel.DialViewModel
import kotlinx.coroutines.delay

internal data class BottomNavItem(
    val section: MainNavSection,
    val label: String,
    val icon: ImageVector
)

private val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(MainNavSection.RECENTS, "RECENTS", Icons.Default.History),
    BottomNavItem(MainNavSection.KEYPAD, "KEYPAD", Icons.Default.Dialpad),
    BottomNavItem(MainNavSection.CONTACTS, "CONTACTS", Icons.Default.Contacts)
)

class MainActivity : ComponentActivity() {

    private var isScreenOffReceiverRegistered = false

    private val pocketPowerButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val session = CallStateRepository.activeCallSession.value
                if (session != null &&
                    session.state == DialCallState.INCOMING &&
                    session.useSlideToAnswer
                ) {
                    CallStateRepository.onPowerLockButtonPressed()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        syncLockScreenFlags(shouldAllowOverLockScreen())
        handleReturnToCallIntent(intent)
        registerPocketPowerButtonReceiver()

        setContent {
            DialAppRoot(initialIntent = intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReturnToCallIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        CallStateRepository.onAppForegroundChanged(true)
        syncLockScreenFlags(shouldAllowOverLockScreen())
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        CallStateRepository.onAppForegroundChanged(false)
        super.onStop()
    }

    override fun onDestroy() {
        unregisterPocketPowerButtonReceiver()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_POWER || keyCode == KeyEvent.KEYCODE_SLEEP) {
            if (CallStateRepository.onPowerLockButtonPressed()) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun registerPocketPowerButtonReceiver() {
        if (isScreenOffReceiverRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pocketPowerButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pocketPowerButtonReceiver, filter)
            }
            isScreenOffReceiverRegistered = true
        } catch (_: Exception) {
            isScreenOffReceiverRegistered = false
        }
    }

    private fun unregisterPocketPowerButtonReceiver() {
        if (!isScreenOffReceiverRegistered) return
        try {
            unregisterReceiver(pocketPowerButtonReceiver)
        } catch (_: Exception) {
        } finally {
            isScreenOffReceiverRegistered = false
        }
    }

    private fun shouldAllowOverLockScreen(): Boolean {
        return CallStateRepository.activeCallSession.value != null
    }

    private fun handleReturnToCallIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(CallNotificationActionReceiver.EXTRA_RETURN_TO_CALL, false) == true) {
            CallStateRepository.setUiMinimized(false)
        }
    }

    fun syncLockScreenFlags(enable: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(enable)
                setTurnScreenOn(enable)
            } else {
                @Suppress("DEPRECATION")
                if (enable) {
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    )
                } else {
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    )
                }
            }
            if (enable) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
fun DialAppRoot(
    initialIntent: Intent? = null,
    viewModel: DialViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsState()
    val activeCall by viewModel.activeCallSession.collectAsState()

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onDefaultDialerRoleResult(result.resultCode == Activity.RESULT_OK)
    }

    val requestDefaultDialerRole: () -> Unit = {
        val intent = viewModel.telecomIntegration.createDefaultDialerRequestIntent()
        if (intent != null) {
            runCatching { roleLauncher.launch(intent) }
        }
    }

    LaunchedEffect(initialIntent) {
        val dataUri = initialIntent?.data
        if (dataUri != null && dataUri.scheme in setOf("tel", "voicemail")) {
            val number = dataUri.schemeSpecificPart.orEmpty()
            if (number.isNotBlank()) {
                viewModel.setDialedNumberFromIntent(number)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDefaultDialerStatus()
                viewModel.refreshDeviceData(force = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(activeCall != null) {
        (context as? MainActivity)?.syncLockScreenFlags(activeCall != null)
    }

    DialTheme(themePreference = settings.themePreference) {
        DialMainHost(
            viewModel = viewModel,
            onRequestDefaultDialerRole = requestDefaultDialerRole
        )
    }
}

@Composable
private fun DialMainHost(
    viewModel: DialViewModel,
    onRequestDefaultDialerRole: () -> Unit
) {
    val currentSection by viewModel.currentSection.collectAsState()
    val dialedNumber by viewModel.dialedNumber.collectAsState()
    val contactsList by viewModel.contactsList.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()
    val keypadMatches by viewModel.keypadMatchingContacts.collectAsState()
    val callHistory by viewModel.callHistoryList.collectAsState()
    val blockedNumbers by viewModel.blockedNumbers.collectAsState()
    val speedDialSlots by viewModel.speedDialSlots.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val statusBanner by viewModel.statusBannerMessage.collectAsState()
    val canUndoDelete by viewModel.canUndoCallLogDelete.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val selectedContact by viewModel.selectedContact.collectAsState()
    val selectedCallLog by viewModel.selectedCallLog.collectAsState()
    val pendingSimNumber by viewModel.pendingSimSelectionNumber.collectAsState()
    val pendingAddContactForm by viewModel.pendingAddContactForm.collectAsState()
    val activeCallSession by viewModel.activeCallSession.collectAsState()
    val postCallSummary by viewModel.postCallSummary.collectAsState()
    val isDefaultDialer by viewModel.isDefaultDialer.collectAsState()
    val shouldShowOnboarding by viewModel.shouldShowDefaultDialerOnboarding.collectAsState()
    val callFilter by viewModel.callFilter.collectAsState()
    val selectedRecentsIds by viewModel.selectedRecentsIds.collectAsState()
    val contactSearchQuery by viewModel.contactSearchQuery.collectAsState()
    val isAiBusy by viewModel.isAiBusy.collectAsState()
    val aiReply by viewModel.aiGeneratedQuickReply.collectAsState()
    val aiError by viewModel.aiErrorMessage.collectAsState()

    val isFullScreenCallVisible = activeCallSession != null &&
        !activeCallSession!!.isUiMinimized &&
        !activeCallSession!!.isIncomingBannerMode

    Box(modifier = Modifier.fillMaxSize()) {
        if (isFullScreenCallVisible && activeCallSession != null) {
            ActiveCallOverlayScreen(
                session = activeCallSession!!,
                contacts = allContacts,
                confirmBeforeEndCall = settings.confirmBeforeEndCall,
                isAiBusy = isAiBusy,
                aiGeneratedReply = aiReply,
                aiErrorMessage = aiError,
                onAnswer = viewModel::answerIncomingCall,
                onReject = viewModel::rejectIncomingCall,
                onSilenceRinger = viewModel::silenceIncomingCallRinger,
                onToggleAnswerMode = viewModel::toggleIncomingAnswerMode,
                onIncomingTouchChanged = viewModel::setIncomingTouchInteraction,
                onMinimizeCallUi = viewModel::minimizeActiveCallScreen,
                onRemindMeLater = viewModel::remindMeLaterOnIncomingCall,
                onSendQuickResponse = { msg ->
                    viewModel.sendQuickResponse(activeCallSession!!.phoneNumber, msg)
                },
                onGenerateAiQuickReply = { ctxPrompt ->
                    viewModel.generateAiSmartQuickReply(
                        activeCallSession!!.contactName.ifBlank { activeCallSession!!.phoneNumber },
                        ctxPrompt
                    )
                },
                onClearAiQuickReply = viewModel::clearAiQuickReply,
                onEndCall = viewModel::endActiveCall,
                onToggleMute = viewModel::toggleMute,
                onSelectAudioRoute = viewModel::selectAudioRoute,
                onCycleSpeaker = viewModel::cycleSpeakerRoute,
                onSendDtmf = viewModel::sendInCallDtmf,
                onToggleKeypad = viewModel::toggleInCallKeypad,
                onToggleHold = viewModel::toggleHoldCall,
                onAddCall = viewModel::addSecondaryCall,
                onSwapCalls = viewModel::swapActiveCalls,
                onMergeCalls = viewModel::mergeCallsIntoConference,
                onRequestVideoCall = viewModel::requestSwitchToVideoCall,
                onSaveContactDuringCall = { num, name ->
                    viewModel.openNewContactSheet(num, name)
                },
                onRefreshContacts = { viewModel.refreshDeviceData(force = true) }
            )
        } else if (postCallSummary != null) {
            PostCallSummaryScreen(
                summary = postCallSummary!!,
                onInteract = viewModel::cancelPostCallAutoReturn,
                onCallAgain = {
                    val number = postCallSummary!!.phoneNumber
                    viewModel.dismissPostCallSummary()
                    viewModel.initiateCall(number)
                },
                onSendMessage = {
                    val number = postCallSummary!!.phoneNumber
                    viewModel.dismissPostCallSummary()
                    viewModel.telecomIntegration.sendQuickResponseSms(number, "")
                },
                onAddContact = {
                    val number = postCallSummary!!.phoneNumber
                    val name = postCallSummary!!.contactName
                    viewModel.dismissPostCallSummary()
                    viewModel.openNewContactSheet(number, name)
                },
                onBlockNumber = viewModel::blockPhoneNumber,
                onUnblockNumber = viewModel::unblockPhoneNumber,
                onDismiss = viewModel::dismissPostCallSummary
            )
        } else if (isSettingsOpen) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.background
            ) {
                DialSettingsScreen(
                    settings = settings,
                    isDefaultDialer = isDefaultDialer,
                    availableSims = viewModel.telecomIntegration.getAvailableSimAccounts(),
                    speedDialSlots = speedDialSlots,
                    blockedNumbers = blockedNumbers,
                    onBack = viewModel::closeSettings,
                    onRequestDefaultDialer = onRequestDefaultDialerRole,
                    onUpdateSettings = viewModel::updateSettings,
                    onAssignSpeedDial = viewModel::assignSpeedDial,
                    onUnblockNumber = viewModel::unblockPhoneNumber,
                    onOpenSystemBlockedNumbers = {
                        viewModel.telecomIntegration.openSystemBlockedNumbers()
                    },
                    onAddBlockedNumber = viewModel::blockPhoneNumber,
                    onResetDefaults = viewModel::resetSettingsToDefault
                )
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.statusBars,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        val session = activeCallSession
                        if (session != null && session.isUiMinimized) {
                            if (session.state == DialCallState.INCOMING) {
                                IncomingCallTopBanner(
                                    session = session,
                                    onExpand = viewModel::restoreActiveCallScreen,
                                    onAnswer = viewModel::answerIncomingCall,
                                    onDecline = viewModel::rejectIncomingCall
                                )
                            } else {
                                OngoingCallReturnBanner(
                                    session = session,
                                    onReturnToCall = viewModel::restoreActiveCallScreen,
                                    onEndCall = viewModel::endActiveCall
                                )
                            }
                        }
                        AnimatedVisibility(visible = shouldShowOnboarding) {
                            DefaultDialerOnboardingOverlay(
                                onRequestRole = onRequestDefaultDialerRole,
                                onContinueAnyway = viewModel::dismissDefaultDialerOnboardingMaybeLater
                            )
                        }
                    }
                },
                bottomBar = {
                    NothingBottomNavBar(
                        currentSection = currentSection,
                        onSelectSection = viewModel::selectMainSection
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Crossfade(
                        targetState = currentSection,
                        animationSpec = tween(durationMillis = 180),
                        label = "main_nav_crossfade"
                    ) { section ->
                        when (section) {
                            MainNavSection.RECENTS -> {
                                RecentsCallHistoryScreen(
                                    callLogs = callHistory,
                                    activeFilter = callFilter,
                                    selectedIds = selectedRecentsIds,
                                    selectedDetailEntry = selectedCallLog,
                                    hasReadCallLogPermission = viewModel.contactsIntegration.hasReadCallLogPermission(),
                                    onRefreshLogs = { viewModel.refreshDeviceData(force = true) },
                                    onSelectFilter = viewModel::setCallFilter,
                                    onToggleSelectId = viewModel::toggleCallLogSelection,
                                    onClearSelection = viewModel::clearCallLogSelection,
                                    onDeleteSelected = viewModel::deleteSelectedCallLogs,
                                    onOpenCallDetail = viewModel::openCallLogDetail,
                                    onCloseCallDetail = viewModel::closeCallLogDetail,
                                    onCallNumber = { num -> viewModel.initiateCall(num) },
                                    onSendSms = { num ->
                                        viewModel.telecomIntegration.sendQuickResponseSms(num, "")
                                    },
                                    onAddContact = { num, name ->
                                        viewModel.openNewContactSheet(num, name)
                                    },
                                    onShareNumber = { name, num ->
                                        viewModel.telecomIntegration.shareText(
                                            "Contact Number",
                                            "${name.ifBlank { "Phone" }}: $num"
                                        )
                                    },
                                    onDeleteSingleLog = viewModel::deleteSingleCallLog,
                                    onBlockNumber = viewModel::blockPhoneNumber,
                                    onOpenSettings = viewModel::openSettings
                                )
                            }
                            MainNavSection.KEYPAD -> {
                                DialerKeypadScreen(
                                    dialedNumber = dialedNumber,
                                    matchingContacts = keypadMatches,
                                    isDefaultDialer = isDefaultDialer,
                                    availableSims = viewModel.telecomIntegration.getAvailableSimAccounts(),
                                    pendingSimNumber = pendingSimNumber,
                                    onRequestDefaultDialer = onRequestDefaultDialerRole,
                                    onOpenSettings = viewModel::openSettings,
                                    onDigitTap = viewModel::onKeypadDigitTap,
                                    onDigitLongPress = viewModel::onKeypadDigitLongPress,
                                    onBackspaceTap = viewModel::onBackspaceTap,
                                    onBackspaceLongPress = viewModel::onBackspaceLongPress,
                                    onPasteNumber = viewModel::onPasteNumber,
                                    onCallClick = { viewModel.initiateCall() },
                                    onSelectSimForCall = { sim ->
                                        val num = pendingSimNumber.orEmpty()
                                        viewModel.initiateCall(num, sim.id)
                                    },
                                    onDismissSimSheet = viewModel::dismissSimSelection,
                                    onSelectMatchedContact = { contact ->
                                        viewModel.initiateCall(contact.primaryNumber)
                                    },
                                    onAddNumberToContacts = { num ->
                                        viewModel.openNewContactSheet(phoneNumber = num)
                                    }
                                )
                            }
                            MainNavSection.CONTACTS -> {
                                ContactsDirectoryScreen(
                                    contacts = contactsList,
                                    callLogs = callHistory,
                                    searchQuery = contactSearchQuery,
                                    selectedContact = selectedContact,
                                    hasReadContactsPermission = viewModel.contactsIntegration.hasReadContactsPermission(),
                                    hasWriteContactsPermission = viewModel.contactsIntegration.hasWriteContactsPermission(),
                                    onRefreshContacts = { viewModel.refreshDeviceData(force = true) },
                                    onSearchQueryChange = viewModel::setContactSearchQuery,
                                    onSelectContact = viewModel::openContactDetail,
                                    onCloseContactDetail = viewModel::closeContactDetail,
                                    onToggleStarred = viewModel::toggleFavouriteContact,
                                    onOpenAddContactSheet = viewModel::openNewContactSheet,
                                    onOpenSettings = viewModel::openSettings,
                                    onSaveStructuredContact = viewModel::saveStructuredContact,
                                    onCallNumber = { num -> viewModel.initiateCall(num) },
                                    onSendSms = { num ->
                                        viewModel.telecomIntegration.sendQuickResponseSms(num, "")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pendingAddContactForm != null) {
            GoogleStyleAddContactSheet(
                initialForm = pendingAddContactForm!!,
                onDismiss = viewModel::dismissNewContactSheet,
                onSaveContact = viewModel::saveStructuredContact
            )
        }

        StatusToastOverlay(
            message = statusBanner,
            canUndo = canUndoDelete,
            onUndo = viewModel::undoDeleteCallLogs,
            onDismiss = viewModel::dismissBanner,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
        )
    }
}

private fun formatDurationSeconds(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
private fun IncomingCallTopBanner(
    session: ActiveCallSession,
    onExpand: () -> Unit,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onExpand),
        color = Color(0xFF1A1A22)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.contactName.ifBlank { session.phoneNumber },
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    text = "Tap to return to incoming call",
                    fontFamily = SpaceMonoFontFamily,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NothingRed)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White)
                }
                IconButton(
                    onClick = onAnswer,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NothingGreen)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun OngoingCallReturnBanner(
    session: ActiveCallSession,
    onReturnToCall: () -> Unit,
    onEndCall: () -> Unit
) {
    var liveDuration by remember(session.durationSeconds) {
        mutableLongStateOf(session.durationSeconds)
    }
    LaunchedEffect(session.connectionTimestampMs, session.state) {
        while (session.state == DialCallState.ACTIVE && session.connectionTimestampMs != null) {
            liveDuration = ((System.currentTimeMillis() - session.connectionTimestampMs) / 1000L)
                .coerceAtLeast(0L)
            delay(1000L)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onReturnToCall)
            .testTag("ongoing_call_return_banner"),
        color = NothingGreen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${session.contactName.ifBlank { session.phoneNumber }} • ${formatDurationSeconds(liveDuration)}",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
            TextButton(onClick = onEndCall) {
                Text(
                    text = "END",
                    fontFamily = DotMatrixFontFamily,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun NothingBottomNavBar(
    currentSection: MainNavSection,
    onSelectSection: (MainNavSection) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BOTTOM_NAV_ITEMS.forEach { item ->
                val isSelected = currentSection == item.section
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onBackground
                            else Color.Transparent
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { onSelectSection(item.section) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("nav_tab_${item.section.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.label,
                            fontFamily = DotMatrixFontFamily,
                            fontSize = 13.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusToastOverlay(
    message: String?,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, NothingRed.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.orEmpty(),
                    fontFamily = SpaceMonoFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (canUndo) {
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(onClick = onUndo) {
                        Text("UNDO", fontFamily = DotMatrixFontFamily, color = NothingRed)
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("OK", fontFamily = SpaceMonoFontFamily, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
