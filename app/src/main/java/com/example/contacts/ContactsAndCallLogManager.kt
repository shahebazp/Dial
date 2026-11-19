package com.example.contacts

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.CallLogEntry
import com.example.data.model.CallRecordType
import com.example.data.model.ContactItem
import com.example.data.model.NewContactFormData

class ContactsAndCallLogManager(private val context: Context) {

    companion object {
        private const val TAG = "ContactsCallLogMgr"
        private val EMERGENCY_NUMBERS = setOf("911", "112", "999", "100", "108", "000")
    }

    fun hasReadContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWriteCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWriteContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun lookupContactNameForNumber(
        phoneNumber: String,
        cachedContacts: List<ContactItem> = emptyList()
    ): String {
        val clean = phoneNumber.trim()
        if (clean.isEmpty()) return "Unknown Caller"
        if (EMERGENCY_NUMBERS.contains(clean)) return "Emergency Services"

        val fromCache = cachedContacts.firstOrNull { contact ->
            contact.phoneNumbers.any { num ->
                PhoneNumberUtils.compare(num, clean) ||
                    num.filter { it.isDigit() }.endsWith(clean.filter { it.isDigit() }.takeLast(7).ifEmpty { "___" })
            }
        }
        if (fromCache != null) return fromCache.name

        if (!hasReadContactsPermission()) return ""

        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(clean)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: ""
                } else {
                    ""
                }
            } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "PhoneLookup query failed", e)
            ""
        }
    }

    fun queryDeviceContacts(): List<ContactItem> {
        if (!hasReadContactsPermission()) return emptyList()
        val contactsMap = LinkedHashMap<Long, MutableContactBuilder>()
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.STARRED,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)
                val lookupIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val number = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isEmpty() && number.isEmpty()) continue
                    val starred = cursor.getInt(starIdx) == 1
                    val lookupKey = cursor.getString(lookupIdx).orEmpty()
                    val lookupUri = if (lookupKey.isNotEmpty()) {
                        ContactsContract.Contacts.getLookupUri(contactId, lookupKey)?.toString().orEmpty()
                    } else {
                        ""
                    }

                    val builder = contactsMap.getOrPut(contactId) {
                        MutableContactBuilder(
                            id = contactId,
                            name = name.ifEmpty { number },
                            isStarred = starred,
                            lookupUri = lookupUri
                        )
                    }
                    if (number.isNotEmpty() && builder.phoneNumbers.none { PhoneNumberUtils.compare(it, number) }) {
                        builder.phoneNumbers.add(number)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
        }
        return contactsMap.values.map { it.build() }
    }

    private class MutableContactBuilder(
        val id: Long,
        val name: String,
        val isStarred: Boolean,
        val lookupUri: String
    ) {
        val phoneNumbers = mutableListOf<String>()
        fun build(): ContactItem = ContactItem(
            id = id,
            name = name,
            phoneNumbers = phoneNumbers,
            isStarred = isStarred,
            lookupUri = lookupUri
        )
    }

    fun queryDeviceCallLog(contacts: List<ContactItem> = emptyList()): List<CallLogEntry> {
        if (!hasReadCallLogPermission()) return emptyList()
        val entries = mutableListOf<CallLogEntry>()
        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < 250) {
                    count++
                    val id = cursor.getLong(idIdx)
                    val number = cursor.getString(numIdx)?.trim().orEmpty()
                    val cachedName = cursor.getString(nameIdx)?.trim().orEmpty()
                    val rawType = cursor.getInt(typeIdx)
                    val dateMs = cursor.getLong(dateIdx)
                    val durationSec = cursor.getLong(durIdx)

                    val recordType = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallRecordType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallRecordType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallRecordType.MISSED
                        CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> CallRecordType.REJECTED
                        else -> CallRecordType.INCOMING
                    }

                    val resolvedName = when {
                        cachedName.isNotEmpty() -> cachedName
                        number.isNotEmpty() -> lookupContactNameForNumber(number, contacts)
                        else -> ""
                    }

                    entries.add(
                        CallLogEntry(
                            id = id,
                            contactName = resolvedName,
                            phoneNumber = number.ifEmpty { "Private Number" },
                            callType = recordType,
                            timestamp = dateMs,
                            duration = durationSec,
                            simLabel = "SIM 1"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying call log", e)
        }
        return entries
    }

    fun deleteDeviceCallLogEntries(ids: List<Long>): Boolean {
        if (!hasWriteCallLogPermission() || ids.isEmpty()) return false
        return try {
            val idList = ids.joinToString(",")
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN ($idList)",
                null
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to delete system call log entries", e)
            false
        }
    }

    fun openAddContactIntent(phoneNumber: String, suggestedName: String = "") {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                if (phoneNumber.isNotBlank()) {
                    putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                }
                if (suggestedName.isNotBlank()) {
                    putExtra(ContactsContract.Intents.Insert.NAME, suggestedName)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch system add contact intent", e)
        }
    }

    fun insertStructuredContact(form: NewContactFormData): Result<Unit> = runCatching {
        if (!hasWriteContactsPermission()) {
            throw SecurityException("WRITE_CONTACTS permission is required")
        }
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.STARRED, if (form.isStarred) 1 else 0)
                .build()
        )

        val fullName = form.fullName.ifBlank { form.phone }
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, fullName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, form.firstName.trim())
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, form.lastName.trim())
                .build()
        )

        if (form.phone.isNotBlank()) {
            val phoneType = when (form.phoneLabel.lowercase()) {
                "work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                else -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            }
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, form.phone.trim())
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneType)
                    .build()
            )
        }

        if (form.email.isNotBlank()) {
            val emailType = when (form.emailLabel.lowercase()) {
                "work" -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
                else -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
            }
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, form.email.trim())
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailType)
                    .build()
            )
        }

        if (form.company.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, form.company.trim())
                    .build()
            )
        }

        if (form.notes.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, form.notes.trim())
                    .build()
            )
        }

        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    fun openEditSystemContactIntent(contact: ContactItem): Boolean {
        return try {
            val uri = if (contact.lookupUri.isNotBlank()) {
                Uri.parse(contact.lookupUri)
            } else {
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)
            }
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                putExtra("finishActivityOnSaveCompleted", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open edit contact intent", e)
            false
        }
    }
}
