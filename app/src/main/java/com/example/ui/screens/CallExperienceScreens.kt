package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.ActiveCallSession
import com.example.data.model.AudioRouteType
import com.example.data.model.ContactItem
import com.example.data.model.DialCallState
import com.example.data.model.PostCallSummary
import com.example.data.model.formatDuration
import com.example.ui.components.CircularCallPulseAvatar
import com.example.ui.components.HorizontalSlideToAnswerBar
import com.example.ui.components.NothingDottedDivider
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.SpaceMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PREDEFINED_QUICK_RESPONSES = listOf(
    "Can't talk right now, I'll call you back soon.",
    "In a meeting — what's up?",
    "On my way! Give me a few minutes.",
    "Please text me instead."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCallOverlayScreen(
    session: ActiveCallSession,
    contacts: List<ContactItem> = emptyList(),
    confirmBeforeEndCall: Boolean = false,
    isAiBusy: Boolean = false,
    aiGeneratedReply: String? = null,
    aiErrorMessage: String? = null,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onSilenceRinger: () -> Unit = {},
    onToggleAnswerMode: () -> Unit = {},
    onIncomingTouchChanged: (Boolean) -> Unit = {},
    onMinimizeCallUi: () -> Unit = {},
    onRemindMeLater: (Int) -> Unit = {},
    onSendQuickResponse: (String) -> Unit,
    onGenerateAiQuickReply: (String) -> Unit,
    onClearAiQuickReply: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectAudioRoute: (AudioRouteType) -> Unit,
    onCycleSpeaker: () -> Unit,
    onSendDtmf: (Char) -> Unit,
    onToggleKeypad: () -> Unit,
    onToggleHold: () -> Unit,
    onAddCall: (String, String) -> Unit,
    onSwapCalls: () -> Unit,
    onMergeCalls: () -> Unit,
    onRequestVideoCall: () -> Unit = {},
    onSaveContactDuringCall: (String, String) -> Unit = { _, _ -> },
    onRefreshContacts: () -> Unit = {}
) {
    val context = LocalContext.current
    var showQuickResponseSheet by remember { mutableStateOf(false) }
    var showRemindMeSheet by remember { mutableStateOf(false) }
    var customQuickReplyText by remember(aiGeneratedReply) {
        mutableStateOf(aiGeneratedReply ?: "")
    }
    var aiPromptContext by remember { mutableStateOf("") }
    var showAudioRouteSheet by remember { mutableStateOf(false) }
    var showMoreActionsSheet by remember { mutableStateOf(false) }
    var showAddCallDialog by remember { mutableStateOf(false) }
    var showEndCallConfirmDialog by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onRefreshContacts()
    }

    val requestEndCallWithConfirmation = {
        if (confirmBeforeEndCall && session.state in setOf(DialCallState.ACTIVE, DialCallState.HOLDING)) {
            showEndCallConfirmDialog = true
        } else {
            onEndCall()
        }
    }

    BackHandler(enabled = true) {
        if (session.isKeypadExpanded) {
            onToggleKeypad()
        } else {
            onMinimizeCallUi()
        }
    }

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0A0A0C),
                Color(0xFF121216),
                Color(0xFF0A0A0C)
            )
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("active_call_overlay"),
        color = Color(0xFF0A0A0C)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            when (session.state) {
                DialCallState.INCOMING -> {
                    IncomingCallScreenContent(
                        session = session,
                        onAnswer = onAnswer,
                        onReject = onReject,
                        onSilenceRinger = onSilenceRinger,
                        onToggleAnswerMode = onToggleAnswerMode,
                        onOpenQuickReply = { showQuickResponseSheet = true },
                        onOpenRemindMe = { showRemindMeSheet = true },
                        onIncomingTouchChanged = onIncomingTouchChanged
                    )
                }
                DialCallState.CONNECTING, DialCallState.DIALLING, DialCallState.RINGING -> {
                    OutgoingCallScreenContent(
                        session = session,
                        onMinimize = onMinimizeCallUi,
                        onToggleMute = onToggleMute,
                        onCycleSpeaker = onCycleSpeaker,
                        onToggleKeypad = onToggleKeypad,
                        onEndCall = requestEndCallWithConfirmation
                    )
                }
                else -> {
                    ConnectedCallScreenContent(
                        session = session,
                        onMinimize = onMinimizeCallUi,
                        onToggleMute = onToggleMute,
                        onSendDtmf = onSendDtmf,
                        onToggleKeypad = onToggleKeypad,
                        onOpenAudioRoutes = {
                            if (session.availableRoutes.size > 2) {
                                showAudioRouteSheet = true
                            } else {
                                onCycleSpeaker()
                            }
                        },
                        onToggleHold = onToggleHold,
                        onOpenAddCall = { showAddCallDialog = true },
                        onSwapCalls = onSwapCalls,
                        onMergeCalls = onMergeCalls,
                        onRequestVideoCall = onRequestVideoCall,
                        onOpenMoreActions = { showMoreActionsSheet = true },
                        onSaveContact = {
                            onSaveContactDuringCall(session.phoneNumber, session.contactName)
                        },
                        onEndCall = requestEndCallWithConfirmation
                    )
                }
            }

            // Earpiece Proximity Screen-Off Curtain
            if (session.isProximityScreenOffActive &&
                session.state in setOf(
                    DialCallState.CONNECTING,
                    DialCallState.DIALLING,
                    DialCallState.RINGING,
                    DialCallState.ACTIVE,
                    DialCallState.HOLDING
                ) &&
                !session.isVideoCall &&
                session.audioRoute == AudioRouteType.EARPIECE
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        }
    }

    if (showEndCallConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndCallConfirmDialog = false },
            title = { Text("End active call?", fontFamily = DotMatrixFontFamily) },
            text = {
                Text("Disconnect call with ${session.contactName.ifBlank { session.phoneNumber }}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndCallConfirmDialog = false
                        onEndCall()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                ) {
                    Text("END CALL", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndCallConfirmDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showRemindMeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRemindMeSheet = false },
            containerColor = Color(0xFF18181D),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "REMIND ME TO CALL BACK",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Decline this call and schedule a high-priority callback reminder for ${session.contactName.ifBlank { session.phoneNumber }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                listOf(
                    15 to "In 15 minutes",
                    30 to "In 30 minutes",
                    60 to "In 1 hour",
                    120 to "In 2 hours"
                ).forEach { (minutes, label) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showRemindMeSheet = false
                                onRemindMeLater(minutes)
                            }
                            .testTag("remind_option_${minutes}m"),
                        color = Color(0xFF24242C)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = label,
                                tint = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showQuickResponseSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showQuickResponseSheet = false
                onClearAiQuickReply()
            },
            containerColor = Color(0xFF18181D),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "DECLINE WITH MESSAGE",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                PREDEFINED_QUICK_RESPONSES.forEachIndexed { idx, preset ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.SEND_SMS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                }
                                showQuickResponseSheet = false
                                onSendQuickResponse(preset)
                                onClearAiQuickReply()
                            }
                            .testTag("quick_reply_preset_$idx"),
                        color = Color(0xFF24242C)
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                NothingDottedDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = aiPromptContext,
                        onValueChange = { aiPromptContext = it },
                        label = { Text("Context for AI smart reply (optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onGenerateAiQuickReply(aiPromptContext) },
                        enabled = !isAiBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                    ) {
                        if (isAiBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Draft AI smart reply"
                            )
                        }
                    }
                }

                if (!aiErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = aiErrorMessage,
                        color = NothingRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = customQuickReplyText,
                    onValueChange = { customQuickReplyText = it },
                    label = { Text("Custom SMS message") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_quick_reply_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val msg = customQuickReplyText.trim()
                        if (msg.isNotEmpty()) {
                            showQuickResponseSheet = false
                            onSendQuickResponse(msg)
                            onClearAiQuickReply()
                        }
                    },
                    enabled = customQuickReplyText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_custom_quick_reply_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("SEND SMS & DECLINE CALL", fontFamily = DotMatrixFontFamily)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAudioRouteSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAudioRouteSheet = false },
            containerColor = Color(0xFF18181D),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "AUDIO OUTPUT",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                session.availableRoutes.forEach { route ->
                    val isSelected = route == session.audioRoute
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showAudioRouteSheet = false
                                onSelectAudioRoute(route)
                            },
                        color = if (isSelected) Color.White else Color(0xFF24242C)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = route.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) Color.Black else Color.White
                            )
                            if (isSelected) {
                                Text(
                                    text = "ACTIVE",
                                    fontFamily = SpaceMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = NothingRed
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showMoreActionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreActionsSheet = false },
            containerColor = Color(0xFF18181D),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "IN-CALL ACTIONS",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                MoreSheetItem(
                    icon = Icons.Default.AddIcCall,
                    title = "Add Call",
                    subtitle = "Place a second call",
                    onClick = {
                        showMoreActionsSheet = false
                        showAddCallDialog = true
                    }
                )
                if (session.hasSecondaryCall) {
                    MoreSheetItem(
                        icon = Icons.Default.SwapCalls,
                        title = "Swap Calls",
                        subtitle = "Switch with ${session.secondaryCallName.ifBlank { session.secondaryCallNumber }}",
                        onClick = {
                            showMoreActionsSheet = false
                            onSwapCalls()
                        }
                    )
                    MoreSheetItem(
                        icon = Icons.Default.CallMerge,
                        title = "Merge Conference",
                        subtitle = "Join both calls into a conference",
                        onClick = {
                            showMoreActionsSheet = false
                            onMergeCalls()
                        }
                    )
                }
                if (session.canSwitchToVideo) {
                    MoreSheetItem(
                        icon = Icons.Default.Videocam,
                        title = "Switch to Video Call",
                        subtitle = "Upgrade to carrier video call",
                        onClick = {
                            showMoreActionsSheet = false
                            onRequestVideoCall()
                        }
                    )
                }
                MoreSheetItem(
                    icon = Icons.Default.PersonAdd,
                    title = "Save to Contacts",
                    subtitle = "Save ${session.phoneNumber} to device contacts",
                    onClick = {
                        showMoreActionsSheet = false
                        onSaveContactDuringCall(session.phoneNumber, session.contactName)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showAddCallDialog) {
        var secondaryNumberInput by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showAddCallDialog = false },
            containerColor = Color(0xFF18181D),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "ADD SECONDARY CALL",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = secondaryNumberInput,
                    onValueChange = { secondaryNumberInput = it },
                    label = { Text("Enter phone number or search contact") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                val hasContactsPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasContactsPerm) {
                    TextButton(
                        onClick = {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    ) {
                        Text("Grant Contacts permission to pick from directory")
                    }
                } else {
                    val filteredContacts = remember(contacts, secondaryNumberInput) {
                        val q = secondaryNumberInput.trim().lowercase()
                        if (q.isEmpty()) contacts.take(6)
                        else contacts.filter {
                            it.name.lowercase().contains(q) ||
                                it.phoneNumbers.any { n -> n.contains(q) }
                        }.take(6)
                    }
                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(filteredContacts, key = { it.id }) { contact ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showAddCallDialog = false
                                        onAddCall(contact.primaryNumber, contact.name)
                                    },
                                color = Color(0xFF24242C)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(contact.name, color = Color.White)
                                    Text(
                                        contact.primaryNumber,
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontFamily = SpaceMonoFontFamily,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val target = secondaryNumberInput.trim()
                        if (target.isNotEmpty()) {
                            showAddCallDialog = false
                            onAddCall(target, "")
                        }
                    },
                    enabled = secondaryNumberInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NothingGreen)
                ) {
                    Text("CALL & HOLD CURRENT", fontFamily = DotMatrixFontFamily, color = Color.White)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun IncomingCallScreenContent(
    session: ActiveCallSession,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onSilenceRinger: () -> Unit = {},
    onToggleAnswerMode: () -> Unit = {},
    onOpenQuickReply: () -> Unit,
    onOpenRemindMe: () -> Unit,
    onIncomingTouchChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var hasTriggeredPrimaryAction by remember(session.callId) { mutableStateOf(false) }

    val contactPhoto by produceState<ImageBitmap?>(initialValue = null, key1 = session.phoneNumber) {
        value = withContext(Dispatchers.IO) {
            loadFullResContactPhoto(context, session.phoneNumber)
        }
    }

    val hasDistinctName = session.contactName.isNotBlank() &&
        session.contactName != session.phoneNumber &&
        !session.contactName.equals("Unknown", ignoreCase = true)
    val primaryHeading = if (hasDistinctName) session.contactName else session.phoneNumber
    val secondaryNumber = if (hasDistinctName) session.phoneNumber else ""

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .testTag("incoming_call_screen")
    ) {
        val topCallerOffset = (maxHeight * 0.14f).coerceIn(72.dp, 148.dp)

        // 1. Full-screen edge-to-edge contact photo with CENTER_CROP
        if (contactPhoto != null) {
            Image(
                bitmap = contactPhoto!!,
                contentDescription = "Caller portrait for $primaryHeading",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Fixed dark gradient overlays at top and bottom for legibility on dark and bright photos
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.72f),
                        0.26f to Color.Black.copy(alpha = 0.28f),
                        0.48f to Color.Transparent,
                        0.66f to Color.Black.copy(alpha = 0.52f),
                        1.0f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )

        // Upper Caller Identity Block (~12-17% of usable screen height, no "Incoming call" text)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = topCallerOffset, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = primaryHeading,
                fontFamily = DotMatrixFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 38.sp,
                lineHeight = 44.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("incoming_caller_name")
            )

            if (secondaryNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = secondaryNumber,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("incoming_caller_number")
                )
            }
        }

        // Lower Control Hierarchy (Normal Mode vs Pocket Mode)
        if (!session.useSlideToAnswer) {
            // 2. NORMAL MODE — Phone not in pocket
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 110.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IncomingSecondaryActionsRow(
                    onOpenQuickReply = onOpenQuickReply,
                    onOpenRemindMe = onOpenRemindMe
                )

                Spacer(modifier = Modifier.height(110.dp))

                // Separate Decline (LEFT, 25% width center) and Answer (RIGHT, 75% width center)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IncomingPrimaryCallButton(
                            label = "Decline",
                            backgroundColor = Color(0xFFFF453A),
                            iconRotationDegrees = 135f,
                            onClick = {
                                if (!hasTriggeredPrimaryAction) {
                                    hasTriggeredPrimaryAction = true
                                    onReject()
                                }
                            },
                            contentDescription = "Decline incoming call",
                            testTag = "incoming_decline_button"
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IncomingPrimaryCallButton(
                            label = "Answer",
                            backgroundColor = Color(0xFF30D158),
                            iconRotationDegrees = 0f,
                            onClick = {
                                if (!hasTriggeredPrimaryAction) {
                                    hasTriggeredPrimaryAction = true
                                    onAnswer()
                                }
                            },
                            contentDescription = "Answer incoming call",
                            testTag = "incoming_answer_button"
                        )
                    }
                }
            }
        } else {
            // 3. POCKET MODE — Slide to Answer
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IncomingSecondaryActionsRow(
                    onOpenQuickReply = onOpenQuickReply,
                    onOpenRemindMe = onOpenRemindMe
                )

                Spacer(modifier = Modifier.height(98.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalSlideToAnswerBar(
                        onAnswer = {
                            if (!hasTriggeredPrimaryAction) {
                                hasTriggeredPrimaryAction = true
                                onAnswer()
                            }
                        },
                        onTouchStateChanged = onIncomingTouchChanged,
                        isAnswered = hasTriggeredPrimaryAction
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomingSecondaryActionsRow(
    onOpenQuickReply: () -> Unit,
    onOpenRemindMe: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            IncomingCircularActionButton(
                icon = Icons.Outlined.Sms,
                label = "Message",
                onClick = onOpenQuickReply,
                testTag = "incoming_message_button"
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            IncomingCircularActionButton(
                icon = Icons.Outlined.Alarm,
                label = "Remind Me",
                onClick = onOpenRemindMe,
                testTag = "incoming_remind_button"
            )
        }
    }
}

@Composable
private fun IncomingCircularActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xCC242424))
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .clickable(onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun IncomingPrimaryCallButton(
    label: String,
    backgroundColor: Color,
    iconRotationDegrees: Float,
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 80 x 80dp minimum touch target wrapping the 72dp circular button
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier
                        .size(30.dp)
                        .rotate(iconRotationDegrees)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp)) // 4dp touch-box padding + 6dp = 10dp visual gap below 72dp circle
        Text(
            text = label,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

private fun loadFullResContactPhoto(context: Context, phoneNumber: String): ImageBitmap? {
    val clean = phoneNumber.trim()
    if (clean.isEmpty()) return null
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }
    return try {
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(clean)
        )
        var contactId: Long? = null
        var photoUriStr: String? = null
        context.contentResolver.query(
            lookupUri,
            arrayOf(
                ContactsContract.PhoneLookup.CONTACT_ID,
                ContactsContract.PhoneLookup.PHOTO_URI
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                contactId = cursor.getLong(0)
                photoUriStr = cursor.getString(1)
            }
        }

        if (!photoUriStr.isNullOrBlank()) {
            context.contentResolver.openInputStream(Uri.parse(photoUriStr))?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) return bmp.asImageBitmap()
            }
        }

        val id = contactId ?: return null
        val contactUri = android.content.ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI,
            id
        )
        ContactsContract.Contacts.openContactPhotoInputStream(
            context.contentResolver,
            contactUri,
            true
        )?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun OutgoingCallScreenContent(
    session: ActiveCallSession,
    onMinimize: () -> Unit,
    onToggleMute: () -> Unit,
    onCycleSpeaker: () -> Unit,
    onToggleKeypad: () -> Unit,
    onEndCall: () -> Unit
) {
    val hasDistinctName = session.contactName.isNotBlank() &&
        session.contactName != session.phoneNumber
    val title = if (hasDistinctName) session.contactName else session.phoneNumber

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize call screen",
                    tint = Color.White
                )
            }
            Text(
                text = "${session.simLabel} • ${session.state.displayLabel}",
                fontFamily = SpaceMonoFontFamily,
                fontSize = 12.sp,
                color = NothingRed
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularCallPulseAvatar(
                displayName = title,
                isActive = true,
                isIncoming = false,
                size = 132.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                fontFamily = DotMatrixFontFamily,
                fontSize = 34.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (hasDistinctName) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = session.phoneNumber,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallControlButton(
                    icon = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (session.isMuted) "Muted" else "Mute",
                    isSelected = session.isMuted,
                    onClick = onToggleMute,
                    testTag = "outgoing_mute_button"
                )
                CallControlButton(
                    icon = Icons.Default.Dialpad,
                    label = "Keypad",
                    isSelected = session.isKeypadExpanded,
                    onClick = onToggleKeypad,
                    testTag = "outgoing_keypad_button"
                )
                CallControlButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = session.audioRoute.label,
                    isSelected = session.audioRoute == AudioRouteType.SPEAKER,
                    onClick = onCycleSpeaker,
                    testTag = "outgoing_speaker_button"
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(NothingRed)
                    .clickable(onClick = onEndCall)
                    .testTag("end_outgoing_call_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectedCallScreenContent(
    session: ActiveCallSession,
    onMinimize: () -> Unit,
    onToggleMute: () -> Unit,
    onSendDtmf: (Char) -> Unit,
    onToggleKeypad: () -> Unit,
    onOpenAudioRoutes: () -> Unit,
    onToggleHold: () -> Unit,
    onOpenAddCall: () -> Unit,
    onSwapCalls: () -> Unit,
    onMergeCalls: () -> Unit,
    onRequestVideoCall: () -> Unit,
    onOpenMoreActions: () -> Unit,
    onSaveContact: () -> Unit = {},
    onEndCall: () -> Unit
) {
    val hasDistinctName = session.contactName.isNotBlank() &&
        session.contactName != session.phoneNumber
    val title = if (hasDistinctName) session.contactName else session.phoneNumber

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMinimize,
                modifier = Modifier.testTag("minimize_call_button")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize call screen",
                    tint = Color.White
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (session.state == DialCallState.HOLDING) "ON HOLD" else formatDuration(session.durationSeconds),
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 20.sp,
                    color = if (session.state == DialCallState.HOLDING) NothingRed else Color.White
                )
                Text(
                    text = session.simLabel,
                    fontFamily = SpaceMonoFontFamily,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onSaveContact) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Save caller to contacts",
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        if (session.hasSecondaryCall) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onSwapCalls),
                color = Color(0xFF1F1F27)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "On Hold: ${session.secondaryCallName.ifBlank { session.secondaryCallNumber }}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "TAP TO SWAP",
                        fontFamily = SpaceMonoFontFamily,
                        fontSize = 11.sp,
                        color = NothingRed
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !session.isKeypadExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularCallPulseAvatar(
                    displayName = title,
                    isActive = session.state == DialCallState.ACTIVE,
                    isIncoming = false,
                    size = 124.dp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = title,
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 32.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasDistinctName) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = session.phoneNumber,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
        }

        if (session.isKeypadExpanded) {
            InCallDtmfKeypad(
                dtmfHistory = session.dtmfHistory,
                onDigitClick = onSendDtmf,
                onCloseKeypad = onToggleKeypad
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallControlButton(
                    icon = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (session.isMuted) "Muted" else "Mute",
                    isSelected = session.isMuted,
                    onClick = onToggleMute,
                    testTag = "incall_mute_button"
                )
                CallControlButton(
                    icon = Icons.Default.Dialpad,
                    label = "Keypad",
                    isSelected = session.isKeypadExpanded,
                    onClick = onToggleKeypad,
                    testTag = "incall_keypad_button"
                )
                val audioIcon = when (session.audioRoute) {
                    AudioRouteType.BLUETOOTH -> Icons.AutoMirrored.Filled.BluetoothSearching
                    AudioRouteType.WIRED_HEADSET -> Icons.Default.Headset
                    AudioRouteType.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                    AudioRouteType.EARPIECE -> Icons.Default.PhoneInTalk
                }
                CallControlButton(
                    icon = audioIcon,
                    label = session.audioRoute.label,
                    isSelected = session.audioRoute == AudioRouteType.SPEAKER ||
                        session.audioRoute == AudioRouteType.BLUETOOTH,
                    onClick = onOpenAudioRoutes,
                    testTag = "incall_audio_route_button"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallControlButton(
                    icon = Icons.Default.Pause,
                    label = if (session.state == DialCallState.HOLDING) "Resume" else "Hold",
                    isSelected = session.state == DialCallState.HOLDING,
                    onClick = onToggleHold,
                    testTag = "incall_hold_button"
                )
                CallControlButton(
                    icon = Icons.Default.AddIcCall,
                    label = "Add Call",
                    isSelected = false,
                    onClick = onOpenAddCall,
                    testTag = "incall_add_call_button"
                )
                CallControlButton(
                    icon = Icons.Default.MoreHoriz,
                    label = "More",
                    isSelected = false,
                    onClick = onOpenMoreActions,
                    testTag = "incall_more_button"
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(NothingRed)
                    .clickable(onClick = onEndCall)
                    .testTag("end_active_call_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor = when {
        !enabled -> Color(0xFF1A1A20).copy(alpha = 0.5f)
        isDestructive && isSelected -> NothingRed
        isSelected -> Color.White
        else -> Color(0xCC242424)
    }
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        isDestructive && isSelected -> Color.White
        isSelected -> Color.Black
        else -> Color.White
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = if (enabled) 0.88f else 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InCallDtmfKeypad(
    dtmfHistory: String,
    onDigitClick: (Char) -> Unit,
    onCloseKeypad: () -> Unit
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF18181F))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dtmfHistory.ifEmpty { "DTMF KEYPAD" },
                fontFamily = DotMatrixFontFamily,
                fontSize = 20.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCloseKeypad) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hide DTMF keypad",
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { digit ->
                    Box(
                        modifier = Modifier
                            .size(width = 76.dp, height = 52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF25252E))
                            .clickable { onDigitClick(digit) }
                            .testTag("incall_dtmf_$digit"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit.toString(),
                            fontFamily = DotMatrixFontFamily,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreSheetItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF24242C)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f)
                )
            }
        }
    }
}

@Composable
fun PostCallSummaryScreen(
    summary: PostCallSummary,
    onInteract: () -> Unit = {},
    onCallAgain: () -> Unit,
    onSendMessage: () -> Unit,
    onAddContact: () -> Unit,
    onBlockNumber: (String) -> Unit = {},
    onUnblockNumber: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var isBlocked by remember { mutableStateOf(false) }
    val displayTitle = summary.contactName.ifBlank { summary.phoneNumber }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("post_call_summary_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.endStatusLabel,
                    fontFamily = SpaceMonoFontFamily,
                    fontSize = 12.sp,
                    color = NothingRed
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close summary"
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (summary.isIncoming) {
                        Icons.AutoMirrored.Filled.CallReceived
                    } else {
                        Icons.AutoMirrored.Filled.CallMade
                    },
                    contentDescription = null,
                    tint = NothingRed,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = displayTitle,
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                if (summary.contactName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = summary.phoneNumber,
                        fontFamily = SpaceMonoFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Duration • ${formatDuration(summary.durationSeconds)}",
                    fontFamily = SpaceMonoFontFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onInteract()
                            onCallAgain()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NothingGreen)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CALL BACK", fontFamily = DotMatrixFontFamily, color = Color.White)
                    }
                    OutlinedButton(
                        onClick = {
                            onInteract()
                            onSendMessage()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MESSAGE", fontFamily = DotMatrixFontFamily)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (summary.contactName.isBlank()) {
                        OutlinedButton(
                            onClick = {
                                onInteract()
                                onAddContact()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ADD CONTACT")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            onInteract()
                            if (isBlocked) {
                                isBlocked = false
                                onUnblockNumber(summary.phoneNumber)
                            } else {
                                isBlocked = true
                                onBlockNumber(summary.phoneNumber)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = NothingRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBlocked) "UNBLOCK" else "BLOCK")
                    }
                }
            }
        }
    }
}
