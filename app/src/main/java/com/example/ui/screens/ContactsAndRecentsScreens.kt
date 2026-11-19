package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallFilter
import com.example.data.model.CallLogEntry
import com.example.data.model.CallRecordType
import com.example.data.model.ContactItem
import com.example.data.model.NewContactFormData
import com.example.data.model.SimAccountInfo
import com.example.data.model.SpeedDialSlot
import com.example.data.model.formatDuration
import com.example.ui.components.NothingDottedDivider
import com.example.ui.components.NothingTopHeader
import com.example.ui.theme.DialThemePreference
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.SpaceMonoFontFamily
import com.example.ui.viewmodel.AppSettingsState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class GroupedCallLogItem(
    val representative: CallLogEntry,
    val count: Int,
    val dayHeader: String
)

private fun formatDayBucketHeader(timestampMs: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val nowDay = now.get(Calendar.DAY_OF_YEAR)
    val targetDay = target.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && nowDay == targetDay -> "TODAY"
        sameYear && nowDay - targetDay == 1 -> "YESTERDAY"
        else -> SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
            .format(Date(timestampMs))
            .uppercase()
    }
}

private fun groupConsecutiveCallLogs(logs: List<CallLogEntry>): List<GroupedCallLogItem> {
    if (logs.isEmpty()) return emptyList()
    val result = mutableListOf<GroupedCallLogItem>()
    var currentRep = logs.first()
    var currentCount = 1
    var currentDay = formatDayBucketHeader(currentRep.timestamp)

    for (i in 1 until logs.size) {
        val item = logs[i]
        val day = formatDayBucketHeader(item.timestamp)
        if (item.phoneNumber == currentRep.phoneNumber &&
            item.callType == currentRep.callType &&
            day == currentDay
        ) {
            currentCount++
        } else {
            result.add(GroupedCallLogItem(currentRep, currentCount, currentDay))
            currentRep = item
            currentCount = 1
            currentDay = day
        }
    }
    result.add(GroupedCallLogItem(currentRep, currentCount, currentDay))
    return result
}

@Composable
fun RecentsCallHistoryScreen(
    callLogs: List<CallLogEntry>,
    activeFilter: CallFilter,
    selectedIds: Set<Long>,
    selectedDetailEntry: CallLogEntry?,
    hasReadCallLogPermission: Boolean,
    onRefreshLogs: () -> Unit,
    onSelectFilter: (CallFilter) -> Unit,
    onToggleSelectId: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenCallDetail: (CallLogEntry) -> Unit,
    onCloseCallDetail: () -> Unit,
    onCallNumber: (String) -> Unit,
    onSendSms: (String) -> Unit,
    onAddContact: (String, String) -> Unit,
    onShareNumber: (String, String) -> Unit,
    onDeleteSingleLog: (CallLogEntry) -> Unit,
    onBlockNumber: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onRefreshLogs()
    }

    if (selectedDetailEntry != null) {
        CallDetailScreen(
            entry = selectedDetailEntry,
            onBack = onCloseCallDetail,
            onCall = { onCallNumber(selectedDetailEntry.phoneNumber) },
            onMessage = { onSendSms(selectedDetailEntry.phoneNumber) },
            onAddContact = {
                onAddContact(selectedDetailEntry.phoneNumber, selectedDetailEntry.contactName)
            },
            onShare = {
                onShareNumber(selectedDetailEntry.contactName, selectedDetailEntry.phoneNumber)
            },
            onDelete = {
                onDeleteSingleLog(selectedDetailEntry)
                onCloseCallDetail()
            },
            onBlock = { onBlockNumber(selectedDetailEntry.phoneNumber) }
        )
        return
    }

    val groupedLogs = remember(callLogs) { groupConsecutiveCallLogs(callLogs) }

    Column(modifier = Modifier.fillMaxSize()) {
        NothingTopHeader(
            title = if (selectedIds.isNotEmpty()) "${selectedIds.size} SELECTED" else "RECENTS",
            subtitle = "${callLogs.size} CALL ENTRIES",
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected call logs",
                                tint = NothingRed
                            )
                        }
                        IconButton(onClick = onClearSelection) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection"
                            )
                        }
                    } else {
                        IconButton(onClick = onRefreshLogs) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh call history"
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open settings"
                            )
                        }
                    }
                }
            }
        )

        // Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CallFilter.entries.toList()) { filter ->
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = {
                        Text(
                            text = filter.name,
                            fontFamily = SpaceMonoFontFamily,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.testTag("call_filter_${filter.name.lowercase()}")
                )
            }
        }

        if (!hasReadCallLogPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SYNC DEVICE CALL LOG",
                            fontFamily = DotMatrixFontFamily,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Grant Call Log permission to load system call history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            callLogPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALL_LOG,
                                    Manifest.permission.WRITE_CALL_LOG
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                    ) {
                        Text("ALLOW", fontFamily = DotMatrixFontFamily, color = Color.White)
                    }
                }
            }
        }

        if (groupedLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO CALL HISTORY",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("recents_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = groupedLogs,
                    key = { it.representative.id }
                ) { grouped ->
                    val entry = grouped.representative
                    val isSelected = selectedIds.contains(entry.id)
                    SwipeableRecentCallRow(
                        entry = entry,
                        count = grouped.count,
                        isSelected = isSelected,
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                onToggleSelectId(entry.id)
                            } else {
                                onOpenCallDetail(entry)
                            }
                        },
                        onLongClick = { onToggleSelectId(entry.id) },
                        onCallClick = { onCallNumber(entry.phoneNumber) },
                        onSmsClick = { onSendSms(entry.phoneNumber) }
                    )
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("DELETE CALL LOGS", fontFamily = DotMatrixFontFamily) },
            text = { Text("Delete ${selectedIds.size} selected call log entries?") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onDeleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                ) {
                    Text("DELETE", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRecentCallRow(
    entry: CallLogEntry,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCallClick: () -> Unit,
    onSmsClick: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val displayTitle = entry.contactName.ifBlank { entry.phoneNumber }
    val isMissed = entry.callType == CallRecordType.MISSED || entry.callType == CallRecordType.REJECTED

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = if (isSelected) NothingRed else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("recent_call_row_${entry.id}"),
        color = if (isSelected) {
            NothingRed.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val typeIcon = when (entry.callType) {
                    CallRecordType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                    CallRecordType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                    CallRecordType.MISSED, CallRecordType.REJECTED -> Icons.AutoMirrored.Filled.CallMissed
                }
                Icon(
                    imageVector = typeIcon,
                    contentDescription = entry.callType.name,
                    tint = if (isMissed) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayTitle,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 16.sp,
                            color = if (isMissed) NothingRed else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (count > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "($count)",
                                fontFamily = SpaceMonoFontFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "${entry.phoneNumber} • ${timeFormatter.format(Date(entry.timestamp))}",
                        fontFamily = SpaceMonoFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSmsClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = "Message ${entry.phoneNumber}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier.testTag("call_recent_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call ${entry.phoneNumber}",
                        tint = NothingGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun CallDetailScreen(
    entry: CallLogEntry,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onAddContact: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "CALL DETAILS",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = entry.contactName.ifBlank { entry.phoneNumber },
                        fontFamily = DotMatrixFontFamily,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = entry.phoneNumber,
                        fontFamily = SpaceMonoFontFamily,
                        fontSize = 15.sp,
                        color = NothingRed
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    NothingDottedDivider()
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "${entry.callType.name} • ${formatDuration(entry.duration)}",
                        fontFamily = SpaceMonoFontFamily,
                        fontSize = 12.sp
                    )
                    Text(
                        text = dateFormat.format(Date(entry.timestamp)),
                        fontFamily = SpaceMonoFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NothingGreen)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CALL", fontFamily = DotMatrixFontFamily, color = Color.White)
                }
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SMS", fontFamily = DotMatrixFontFamily)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(entry.phoneNumber))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COPY")
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SHARE")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (entry.contactName.isBlank()) {
                    OutlinedButton(
                        onClick = onAddContact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE")
                    }
                }
                OutlinedButton(
                    onClick = onBlock,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = NothingRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BLOCK")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = NothingRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("DELETE")
                }
            }
        }
    }
}

@Composable
fun ContactsDirectoryScreen(
    contacts: List<ContactItem>,
    callLogs: List<CallLogEntry>,
    searchQuery: String,
    selectedContact: ContactItem?,
    hasReadContactsPermission: Boolean,
    hasWriteContactsPermission: Boolean,
    onRefreshContacts: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectContact: (ContactItem) -> Unit,
    onCloseContactDetail: () -> Unit,
    onToggleStarred: (ContactItem) -> Unit,
    onOpenAddContactSheet: (String, String, String) -> Unit = { _, _, _ -> },
    onOpenSettings: () -> Unit,
    onSaveStructuredContact: (NewContactFormData) -> Unit = {},
    onCallNumber: (String) -> Unit,
    onSendSms: (String) -> Unit
) {
    var showLocalAddSheet by remember { mutableStateOf(false) }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onRefreshContacts()
    }

    if (selectedContact != null) {
        ContactDetailScreen(
            contact = selectedContact,
            callLogs = callLogs,
            onBack = onCloseContactDetail,
            onCallNumber = onCallNumber,
            onSendSms = onSendSms,
            onToggleStarred = { onToggleStarred(selectedContact) }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            NothingTopHeader(
                title = "CONTACTS",
                subtitle = "${contacts.size} SAVED CONTACTS",
                trailingContent = {
                    Row {
                        IconButton(onClick = onRefreshContacts) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh contacts")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Open settings")
                        }
                    }
                }
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search name, number, or company") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("contact_search_input")
            )

            if (!hasReadContactsPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SYNC DEVICE CONTACTS",
                                fontFamily = DotMatrixFontFamily,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Allow Contacts permission to search and call saved contacts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                contactsPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.WRITE_CONTACTS
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                        ) {
                            Text("ALLOW", fontFamily = DotMatrixFontFamily, color = Color.White)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("contacts_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                            .clickable { onSelectContact(contact) }
                            .testTag("contact_row_${contact.id}"),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.name.take(1).uppercase(),
                                        fontFamily = DotMatrixFontFamily,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = contact.primaryNumber,
                                        fontFamily = SpaceMonoFontFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { onCallNumber(contact.primaryNumber) }) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call ${contact.name}",
                                    tint = NothingGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                showLocalAddSheet = true
                onOpenAddContactSheet("", "", "")
            },
            containerColor = NothingRed,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_contact_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Contact")
        }
    }

    if (showLocalAddSheet) {
        GoogleStyleAddContactSheet(
            onDismiss = { showLocalAddSheet = false },
            onSaveContact = { form ->
                showLocalAddSheet = false
                onSaveStructuredContact(form)
            }
        )
    }
}

@Composable
private fun ContactDetailScreen(
    contact: ContactItem,
    callLogs: List<CallLogEntry>,
    onBack: () -> Unit,
    onCallNumber: (String) -> Unit,
    onSendSms: (String) -> Unit,
    onToggleStarred: () -> Unit
) {
    BackHandler(onBack = onBack)
    val matchingHistory = remember(contact, callLogs) {
        callLogs.filter { it.phoneNumber == contact.primaryNumber }.take(10)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = onToggleStarred) {
                Icon(
                    imageVector = if (contact.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Toggle favourite",
                    tint = if (contact.isStarred) NothingRed else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = contact.name,
            fontFamily = DotMatrixFontFamily,
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        contact.phoneNumbers.forEach { number ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = number,
                            fontFamily = SpaceMonoFontFamily,
                            fontSize = 15.sp
                        )
                        Text(
                            text = contact.phoneLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = { onSendSms(number) }) {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Message")
                        }
                        IconButton(onClick = { onCallNumber(number) }) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = NothingGreen)
                        }
                    }
                }
            }
        }

        if (matchingHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "RECENT CALLS",
                fontFamily = DotMatrixFontFamily,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(matchingHistory, key = { it.id }) { log ->
                    Text(
                        text = "${log.callType.name} • ${formatDuration(log.duration)}",
                        fontFamily = SpaceMonoFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleStyleAddContactSheet(
    initialForm: NewContactFormData = NewContactFormData(),
    onDismiss: () -> Unit,
    onSaveContact: (NewContactFormData) -> Unit
) {
    var firstName by remember { mutableStateOf(initialForm.firstName) }
    var lastName by remember { mutableStateOf(initialForm.lastName) }
    var company by remember { mutableStateOf(initialForm.company) }
    var phone by remember { mutableStateOf(initialForm.phone) }
    var email by remember { mutableStateOf(initialForm.email) }
    var notes by remember { mutableStateOf(initialForm.notes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "NEW CONTACT",
                fontFamily = DotMatrixFontFamily,
                fontSize = 22.sp
            )
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("Company") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    onSaveContact(
                        NewContactFormData(
                            firstName = firstName,
                            lastName = lastName,
                            company = company,
                            phone = phone,
                            email = email,
                            notes = notes
                        )
                    )
                },
                enabled = firstName.isNotBlank() || phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE CONTACT", fontFamily = DotMatrixFontFamily, color = Color.White)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DialSettingsScreen(
    settings: AppSettingsState,
    isDefaultDialer: Boolean,
    availableSims: List<SimAccountInfo>,
    speedDialSlots: List<SpeedDialSlot>,
    blockedNumbers: List<String>,
    onBack: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onUpdateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit,
    onAssignSpeedDial: (Int, String, String) -> Unit = { _, _, _ -> },
    onUnblockNumber: (String) -> Unit = {},
    onOpenSystemBlockedNumbers: () -> Unit,
    onAddBlockedNumber: (String) -> Unit,
    onResetDefaults: () -> Unit
) {
    BackHandler(onBack = onBack)

    var showBlockDialog by remember { mutableStateOf(false) }
    var blockInput by remember { mutableStateOf("") }
    var editingSpeedDialDigit by remember { mutableIntStateOf(0) }
    var speedDialNameInput by remember { mutableStateOf("") }
    var speedDialNumberInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "DIAL SETTINGS",
                fontFamily = DotMatrixFontFamily,
                fontSize = 24.sp
            )
        }

        SettingsSectionCard(title = "DEFAULT PHONE ROLE") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDefaultDialer) "Active default dialer" else "Not set as default dialer",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!isDefaultDialer) {
                    Button(
                        onClick = onRequestDefaultDialer,
                        colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                    ) {
                        Text("SET DEFAULT", fontFamily = DotMatrixFontFamily, color = Color.White)
                    }
                }
            }
        }

        SettingsSectionCard(title = "APPEARANCE & SOUND") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialThemePreference.entries.forEach { pref ->
                    FilterChip(
                        selected = settings.themePreference == pref,
                        onClick = {
                            onUpdateSettings { it.copy(themePreference = pref) }
                        },
                        label = {
                            Text(pref.displayLabel, fontFamily = SpaceMonoFontFamily, fontSize = 11.sp)
                        }
                    )
                }
            }

            SettingsToggleRow(
                title = "Keypad DTMF Tones",
                subtitle = "Play audible tones when dialing digits",
                checked = settings.dialPadSoundsEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(dialPadSoundsEnabled = enabled) }
                }
            )
            SettingsToggleRow(
                title = "Haptic Feedback",
                subtitle = "Tactile vibration on keypad & sliders",
                checked = settings.hapticFeedbackEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(hapticFeedbackEnabled = enabled) }
                }
            )
            SettingsToggleRow(
                title = "Confirm Before Ending Call",
                subtitle = "Show confirmation dialog before disconnecting active calls",
                checked = settings.confirmBeforeEndCall,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(confirmBeforeEndCall = enabled) }
                }
            )
        }

        SettingsSectionCard(title = "SPEED DIAL (KEYS 2–9)") {
            speedDialSlots.forEach { slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Key ${slot.digit}: ${slot.contactName.ifBlank { slot.phoneNumber.ifBlank { "Unassigned" } }}",
                        fontFamily = SpaceMonoFontFamily,
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = {
                            editingSpeedDialDigit = slot.digit
                            speedDialNameInput = slot.contactName
                            speedDialNumberInput = slot.phoneNumber
                        }
                    ) {
                        Text("EDIT")
                    }
                }
            }
        }

        SettingsSectionCard(title = "BLOCKED NUMBERS (${blockedNumbers.size})") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showBlockDialog = true }) {
                    Text("ADD NUMBER")
                }
                OutlinedButton(onClick = onOpenSystemBlockedNumbers) {
                    Text("SYSTEM BLOCKLIST")
                }
            }
            blockedNumbers.forEach { num ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(num, fontFamily = SpaceMonoFontFamily, fontSize = 13.sp)
                    TextButton(onClick = { onUnblockNumber(num) }) {
                        Text("UNBLOCK", color = NothingRed)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onResetDefaults,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("RESET SETTINGS TO DEFAULT", fontFamily = DotMatrixFontFamily)
        }
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("BLOCK PHONE NUMBER", fontFamily = DotMatrixFontFamily) },
            text = {
                OutlinedTextField(
                    value = blockInput,
                    onValueChange = { blockInput = it },
                    label = { Text("Phone number") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (blockInput.isNotBlank()) {
                            onAddBlockedNumber(blockInput.trim())
                            blockInput = ""
                        }
                        showBlockDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                ) {
                    Text("BLOCK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (editingSpeedDialDigit in 2..9) {
        AlertDialog(
            onDismissRequest = { editingSpeedDialDigit = 0 },
            title = { Text("SPEED DIAL KEY $editingSpeedDialDigit", fontFamily = DotMatrixFontFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = speedDialNameInput,
                        onValueChange = { speedDialNameInput = it },
                        label = { Text("Contact label") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = speedDialNumberInput,
                        onValueChange = { speedDialNumberInput = it },
                        label = { Text("Phone number") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAssignSpeedDial(
                            editingSpeedDialDigit,
                            speedDialNameInput.trim(),
                            speedDialNumberInput.trim()
                        )
                        editingSpeedDialDigit = 0
                    }
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSpeedDialDigit = 0 }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontFamily = DotMatrixFontFamily,
                fontSize = 15.sp,
                color = NothingRed
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
