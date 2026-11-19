package com.example.data.repository

import android.content.Context
import com.example.contacts.ContactsAndCallLogManager
import com.example.data.local.BlockedNumberEntity
import com.example.data.local.CallLogEntryEntity
import com.example.data.local.DialDao
import com.example.data.local.DialDatabase
import com.example.data.local.SpeedDialEntity
import com.example.data.model.CallLogEntry
import com.example.data.model.CallRecordType
import com.example.data.model.ContactItem
import com.example.data.model.SpeedDialSlot
import com.example.telecom.TelecomIntegrationManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DialRepository(
    private val context: Context,
    private val database: DialDatabase = DialDatabase.getDatabase(context),
    private val dao: DialDao = database.dialDao(),
    val telecomManager: TelecomIntegrationManager = TelecomIntegrationManager(context),
    val contactsManager: ContactsAndCallLogManager = ContactsAndCallLogManager(context)
) {

    val persistedCallLogsFlow: Flow<List<CallLogEntry>> =
        dao.observeCallLogEntries().map { list ->
            list.map { entity ->
                val recordType = runCatching { CallRecordType.valueOf(entity.callType) }
                    .getOrDefault(CallRecordType.INCOMING)
                CallLogEntry(
                    id = entity.id,
                    contactName = entity.contactName,
                    phoneNumber = entity.phoneNumber,
                    callType = recordType,
                    timestamp = entity.timestamp,
                    duration = entity.duration,
                    simLabel = entity.simLabel
                )
            }
        }

    val speedDialFlow: Flow<List<SpeedDialSlot>> =
        dao.observeSpeedDialSlots().map { list ->
            val byDigit = list.associateBy { it.digit }
            (2..9).map { digit ->
                val found = byDigit[digit]
                SpeedDialSlot(
                    digit = digit,
                    contactName = found?.contactName ?: "",
                    phoneNumber = found?.phoneNumber ?: ""
                )
            }
        }

    val blockedNumbersFlow: Flow<List<String>> =
        dao.observeBlockedNumbers().map { list ->
            list.map { it.phoneNumber }
        }

    suspend fun cleanupLegacyStorageArtifacts(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            listOf("recordings", "call_recordings", "voicemails", "visual_voicemail").forEach { dirName ->
                val dir = File(context.filesDir, dirName)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
            true
        }
    }

    suspend fun recordRealCallEvent(
        phoneNumber: String,
        contactName: String,
        callType: CallRecordType,
        timestamp: Long,
        durationSeconds: Long,
        simLabel: String
    ): Long = withContext(Dispatchers.IO) {
        val cleanNumber = phoneNumber.trim().ifEmpty { "Unknown" }
        val existing = dao.findDuplicateCallLog(cleanNumber, timestamp)
        if (existing != null) {
            return@withContext existing.id
        }
        val resolvedName = contactName.ifBlank {
            contactsManager.lookupContactNameForNumber(cleanNumber)
        }
        dao.insertCallLogEntry(
            CallLogEntryEntity(
                phoneNumber = cleanNumber,
                timestamp = timestamp,
                duration = durationSeconds,
                callType = callType.name,
                contactName = resolvedName,
                simLabel = simLabel
            )
        )
    }

    suspend fun syncProviderCallLogsToRoom(contacts: List<ContactItem> = emptyList()) =
        withContext(Dispatchers.IO) {
            if (!contactsManager.hasReadCallLogPermission()) return@withContext
            val providerLogs = contactsManager.queryDeviceCallLog(contacts)
            if (providerLogs.isEmpty()) return@withContext

            val existingKeys = dao.getExistingCallKeys()
            val toInsert = providerLogs.filter { entry ->
                existingKeys.none { key ->
                    key.phoneNumber == entry.phoneNumber &&
                        kotlin.math.abs(key.timestamp - entry.timestamp) < 3000L
                }
            }.map { entry ->
                CallLogEntryEntity(
                    phoneNumber = entry.phoneNumber,
                    timestamp = entry.timestamp,
                    duration = entry.duration,
                    callType = entry.callType.name,
                    contactName = entry.contactName,
                    simLabel = entry.simLabel
                )
            }
            if (toInsert.isNotEmpty()) {
                dao.insertCallLogEntries(toInsert)
            }
        }

    suspend fun deleteCallLogs(ids: List<Long>): Boolean = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext false
        dao.deleteCallLogEntriesByIds(ids)
        contactsManager.deleteDeviceCallLogEntries(ids)
        true
    }

    suspend fun saveSpeedDial(digit: Int, contactName: String, phoneNumber: String) =
        withContext(Dispatchers.IO) {
            if (digit !in 2..9) return@withContext
            dao.upsertSpeedDialSlot(
                SpeedDialEntity(
                    digit = digit,
                    contactName = contactName.trim(),
                    phoneNumber = phoneNumber.trim()
                )
            )
        }

    suspend fun addBlockedNumber(number: String) = withContext(Dispatchers.IO) {
        val clean = number.trim()
        if (clean.isEmpty()) return@withContext
        dao.insertBlockedNumber(
            BlockedNumberEntity(
                phoneNumber = clean,
                addedTimestampMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeBlockedNumber(number: String) = withContext(Dispatchers.IO) {
        dao.removeBlockedNumber(number.trim())
    }
}
