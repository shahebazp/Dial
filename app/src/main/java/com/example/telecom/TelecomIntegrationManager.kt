package com.example.telecom

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.BlockedNumberContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.AudioRouteType
import com.example.data.model.SimAccountInfo

class TelecomIntegrationManager(private val context: Context) {

    companion object {
        private const val TAG = "TelecomIntegration"
    }

    private val telecomManager: TelecomManager? =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun isDefaultDialer(): Boolean {
        val tm = telecomManager ?: return false
        return try {
            tm.defaultDialerPackage == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    fun isRoleDialerAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        } else {
            telecomManager != null
        }
    }

    fun createDefaultDialerRequestIntent(): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                } else {
                    Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                        putExtra(
                            TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                            context.packageName
                        )
                    }
                }
            } else {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(
                        TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                        context.packageName
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to create default dialer intent", e)
            null
        }
    }

    fun getAvailableSimAccounts(): List<SimAccountInfo> {
        val tm = telecomManager ?: return listOf(
            SimAccountInfo(id = "sim_1", slotIndex = 0, label = "SIM 1", carrierName = "Carrier")
        )
        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasReadPhoneState) {
            return listOf(
                SimAccountInfo(id = "sim_1", slotIndex = 0, label = "SIM 1", carrierName = "Mobile Network")
            )
        }
        return try {
            val handles: List<PhoneAccountHandle> = tm.callCapablePhoneAccounts
            if (handles.isEmpty()) {
                listOf(
                    SimAccountInfo(id = "sim_1", slotIndex = 0, label = "SIM 1", carrierName = "Mobile Network")
                )
            } else {
                handles.mapIndexed { index, handle ->
                    val account: PhoneAccount? = tm.getPhoneAccount(handle)
                    val label = account?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${index + 1}"
                    val address = account?.address?.schemeSpecificPart.orEmpty()
                    val supportsVideo = account?.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING) == true
                    SimAccountInfo(
                        id = handle.id,
                        slotIndex = index,
                        label = "SIM ${index + 1}",
                        carrierName = label,
                        phoneNumber = address,
                        supportsVideoCalling = supportsVideo
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying SIM accounts", e)
            listOf(
                SimAccountInfo(id = "sim_1", slotIndex = 0, label = "SIM 1", carrierName = "Mobile Network")
            )
        }
    }

    fun isVideoCallingAvailable(): Boolean {
        return getAvailableSimAccounts().any { it.supportsVideoCalling }
    }

    fun getAvailableAudioRoutes(): List<AudioRouteType> {
        val routes = mutableSetOf(AudioRouteType.EARPIECE, AudioRouteType.SPEAKER)
        val am = audioManager ?: return routes.toList()
        try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER -> routes.add(AudioRouteType.BLUETOOTH)
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> routes.add(AudioRouteType.WIRED_HEADSET)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query audio devices", e)
        }
        return routes.toList()
    }

    fun placeOutgoingCall(rawNumber: String, simId: String? = null): Result<Boolean> = runCatching {
        val clean = rawNumber.trim()
        require(clean.isNotEmpty()) { "Phone number is empty" }
        val uri = Uri.fromParts("tel", clean, null)
        val hasCallPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCallPhone && telecomManager != null) {
            val extras = Bundle()
            if (!simId.isNullOrBlank() &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val matchingHandle = telecomManager.callCapablePhoneAccounts.firstOrNull { it.id == simId }
                if (matchingHandle != null) {
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, matchingHandle)
                }
            }
            telecomManager.placeCall(uri, extras)
            true
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            false
        }
    }

    fun sendQuickResponseSms(phoneNumber: String, message: String): Pair<Boolean, String> {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isEmpty()) return false to "Caller number unavailable"
        val hasSendSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSendSms) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                if (smsManager != null) {
                    smsManager.sendTextMessage(cleanNumber, null, message, null, null)
                    return true to "SMS reply sent to $cleanNumber"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct SmsManager failed, falling back to SMS intent", e)
            }
        }

        return try {
            val smsUri = Uri.parse("smsto:${Uri.encode(cleanNumber)}")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true to "Opened messaging app with quick reply"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SMS app", e)
            false to "Unable to open messaging app"
        }
    }

    fun shareText(title: String, text: String) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to share text", e)
        }
    }

    fun openSystemBlockedNumbers(): Boolean {
        return try {
            val tm = telecomManager ?: return false
            val intent = tm.createManageBlockedNumbersIntent() ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open system blocked numbers", e)
            false
        }
    }

    fun canBlockNumbersNatively(): Boolean {
        return try {
            isDefaultDialer() && BlockedNumberContract.canCurrentUserBlockNumbers(context)
        } catch (e: Exception) {
            false
        }
    }

    fun triggerVibrationPulse(enabled: Boolean) {
        if (!enabled) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(18L)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration pulse failed", e)
        }
    }
}
