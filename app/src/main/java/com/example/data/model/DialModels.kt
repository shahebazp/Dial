package com.example.data.model

enum class MainNavSection {
    RECENTS,
    KEYPAD,
    CONTACTS
}

enum class DialCallState(val displayLabel: String) {
    INCOMING("INCOMING CALL"),
    CONNECTING("CONNECTING..."),
    DIALLING("DIALLING..."),
    RINGING("RINGING..."),
    ACTIVE("CONNECTED"),
    HOLDING("ON HOLD"),
    DISCONNECTED("CALL ENDED"),
    REJECTED("CALL DECLINED"),
    FAILED("CALL FAILED"),
    BUSY("LINE BUSY"),
    NO_NETWORK("NO MOBILE NETWORK")
}

enum class CallDirection {
    INCOMING,
    OUTGOING
}

enum class AudioRouteType(val label: String) {
    EARPIECE("Earpiece"),
    SPEAKER("Speaker"),
    BLUETOOTH("Bluetooth"),
    WIRED_HEADSET("Headset")
}

data class SimAccountInfo(
    val id: String,
    val slotIndex: Int,
    val label: String,
    val carrierName: String,
    val phoneNumber: String = "",
    val supportsVideoCalling: Boolean = false
)

data class ActiveCallSession(
    val callId: String,
    val contactName: String,
    val phoneNumber: String,
    val direction: CallDirection,
    val state: DialCallState,
    val simLabel: String = "SIM 1",
    val isMuted: Boolean = false,
    val audioRoute: AudioRouteType = AudioRouteType.EARPIECE,
    val availableRoutes: List<AudioRouteType> = listOf(AudioRouteType.EARPIECE, AudioRouteType.SPEAKER),
    val connectionTimestampMs: Long? = null,
    val disconnectionTimestampMs: Long? = null,
    val durationSeconds: Long = 0L,
    val isKeypadExpanded: Boolean = false,
    val dtmfHistory: String = "",
    val hasSecondaryCall: Boolean = false,
    val secondaryCallName: String = "",
    val secondaryCallNumber: String = "",
    val isConference: Boolean = false,
    val canSwitchToVideo: Boolean = false,
    val isVideoCall: Boolean = false,
    val isRingerSilenced: Boolean = false,
    val useSlideToAnswer: Boolean = false,
    val isPocketModeLatched: Boolean = false,
    val isManualModeOverride: Boolean = false,
    val isUserTouchingIncomingControls: Boolean = false,
    val isUiMinimized: Boolean = false,
    val isIncomingBannerMode: Boolean = false,
    val isProximityScreenOffActive: Boolean = false
) {
    val isIncoming: Boolean
        get() = direction == CallDirection.INCOMING
}

data class PostCallSummary(
    val contactName: String,
    val phoneNumber: String,
    val direction: CallDirection,
    val durationSeconds: Long,
    val timestampMs: Long,
    val endStatusLabel: String
) {
    val isIncoming: Boolean
        get() = direction == CallDirection.INCOMING
}

enum class CallRecordType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED
}

enum class CallFilter {
    ALL,
    MISSED,
    INCOMING,
    OUTGOING
}

data class CallLogEntry(
    val id: Long = 0,
    val contactName: String,
    val phoneNumber: String,
    val callType: CallRecordType,
    val timestamp: Long,
    val duration: Long,
    val simLabel: String = "SIM 1"
) {
    val timestampMs: Long
        get() = timestamp
    val durationSeconds: Long
        get() = duration
}

data class ContactItem(
    val id: Long,
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String> = emptyList(),
    val isStarred: Boolean = false,
    val lookupUri: String = "",
    val company: String = "",
    val notes: String = "",
    val phoneLabel: String = "Mobile",
    val emailLabel: String = "Personal"
) {
    val primaryNumber: String
        get() = phoneNumbers.firstOrNull() ?: ""
}

data class NewContactFormData(
    val firstName: String = "",
    val lastName: String = "",
    val company: String = "",
    val phone: String = "",
    val phoneLabel: String = "Mobile",
    val email: String = "",
    val emailLabel: String = "Personal",
    val notes: String = "",
    val isStarred: Boolean = false
) {
    val fullName: String
        get() = listOf(firstName.trim(), lastName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}

data class SpeedDialSlot(
    val digit: Int,
    val contactName: String,
    val phoneNumber: String
)

fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
