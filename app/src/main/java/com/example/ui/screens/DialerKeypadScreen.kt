package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.ContactItem
import com.example.data.model.SimAccountInfo
import com.example.ui.components.NothingDottedDivider
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.SpaceMonoFontFamily

internal data class KeypadKeySpec(
    val digit: Char,
    val letters: String
)

private val KEYPAD_ROWS = listOf(
    listOf(
        KeypadKeySpec('1', "SPD"),
        KeypadKeySpec('2', "ABC"),
        KeypadKeySpec('3', "DEF")
    ),
    listOf(
        KeypadKeySpec('4', "GHI"),
        KeypadKeySpec('5', "JKL"),
        KeypadKeySpec('6', "MNO")
    ),
    listOf(
        KeypadKeySpec('7', "PQRS"),
        KeypadKeySpec('8', "TUV"),
        KeypadKeySpec('9', "WXYZ")
    ),
    listOf(
        KeypadKeySpec('*', ""),
        KeypadKeySpec('0', "+"),
        KeypadKeySpec('#', "")
    )
)

@Composable
fun DefaultDialerOnboardingOverlay(
    onRequestRole: () -> Unit,
    onContinueAnyway: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, NothingRed.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .testTag("default_dialer_onboarding_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhoneCallback,
                    contentDescription = null,
                    tint = NothingRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SET DIAL AS DEFAULT PHONE APP",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enable full Nothing OS incoming call screen, Pocket Mode slide-to-answer, and Telecom call controls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRequestRole,
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("set_default_dialer_button")
                ) {
                    Text(
                        text = "SET DEFAULT",
                        fontFamily = DotMatrixFontFamily,
                        color = Color.White
                    )
                }
                OutlinedButton(
                    onClick = onContinueAnyway,
                    modifier = Modifier.testTag("dismiss_onboarding_button")
                ) {
                    Text("LATER", fontFamily = SpaceMonoFontFamily, fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialerKeypadScreen(
    dialedNumber: String,
    matchingContacts: List<ContactItem>,
    isDefaultDialer: Boolean,
    availableSims: List<SimAccountInfo>,
    pendingSimNumber: String?,
    onRequestDefaultDialer: () -> Unit,
    onOpenSettings: () -> Unit,
    onDigitTap: (Char) -> Unit,
    onDigitLongPress: (Char) -> Unit,
    onBackspaceTap: () -> Unit,
    onBackspaceLongPress: () -> Unit,
    onPasteNumber: (String) -> Unit,
    onCallClick: () -> Unit,
    onSelectSimForCall: (SimAccountInfo) -> Unit,
    onDismissSimSheet: () -> Unit,
    onSelectMatchedContact: (ContactItem) -> Unit,
    onAddNumberToContacts: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onCallClick()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DIAL",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isDefaultDialer) "TELECOM ACTIVE" else "STANDARD MODE",
                    fontFamily = SpaceMonoFontFamily,
                    fontSize = 10.sp,
                    color = if (isDefaultDialer) NothingGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isDefaultDialer) {
                    OutlinedButton(
                        onClick = onRequestDefaultDialer,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text("DEFAULT", fontFamily = SpaceMonoFontFamily, fontSize = 10.sp)
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // T9 / Contact Suggestions & Add to Contacts Action
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = matchingContacts.isNotEmpty(),
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 136.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(matchingContacts.take(3), key = { it.id }) { contact ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectMatchedContact(contact) }
                                .testTag("keypad_suggestion_${contact.id}"),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contact.name,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = contact.primaryNumber,
                                    fontFamily = SpaceMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = NothingRed
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = dialedNumber.isNotBlank() && matchingContacts.isEmpty(),
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = { onAddNumberToContacts(dialedNumber) },
                        modifier = Modifier.testTag("add_dialed_to_contacts_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ADD TO CONTACTS",
                            fontFamily = SpaceMonoFontFamily,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Dialed Number Readout Row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        val clipText = clipboard.getText()?.text?.trim().orEmpty()
                        if (clipText.isNotEmpty()) {
                            onPasteNumber(clipText)
                        }
                    },
                    modifier = Modifier.testTag("paste_number_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste number",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = dialedNumber,
                        transitionSpec = {
                            fadeIn(tween(110)) togetherWith fadeOut(tween(90))
                        },
                        label = "dialed_number_readout"
                    ) { currentText ->
                        Text(
                            text = currentText.ifEmpty { "ENTER NUMBER" },
                            fontFamily = DotMatrixFontFamily,
                            fontSize = when {
                                currentText.isEmpty() -> 18.sp
                                currentText.length > 13 -> 24.sp
                                currentText.length > 10 -> 28.sp
                                else -> 34.sp
                            },
                            color = if (currentText.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("dialed_number_display")
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = dialedNumber.isNotEmpty(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBackspaceTap()
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBackspaceLongPress()
                            }
                        )
                        .testTag("keypad_backspace_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = if (dialedNumber.isNotEmpty()) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            NothingDottedDivider()
            Spacer(modifier = Modifier.height(10.dp))

            // 12-Key Keypad Grid
            KEYPAD_ROWS.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { keySpec ->
                        KeypadOvalButton(
                            digit = keySpec.digit,
                            letters = keySpec.letters,
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigitTap(keySpec.digit)
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDigitLongPress(keySpec.digit)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Call Pill Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val hasCallPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasCallPerm && dialedNumber.isNotBlank()) {
                        callPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_PHONE_STATE
                            )
                        )
                    } else {
                        onCallClick()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(34.dp),
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(64.dp)
                    .testTag("place_call_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Place Call",
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "CALL",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (!pendingSimNumber.isNullOrBlank()) {
        ModalBottomSheet(
            onDismissRequest = onDismissSimSheet,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "SELECT SIM FOR $pendingSimNumber",
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                availableSims.forEach { sim ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectSimForCall(sim) },
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = sim.label,
                                tint = NothingRed
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(sim.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    sim.carrierName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadOvalButton(
    digit: Char,
    letters: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "keypad_key_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isPressed) NothingRed else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(32.dp)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress
            )
            .testTag("keypad_button_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit.toString(),
                fontFamily = DotMatrixFontFamily,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontFamily = SpaceMonoFontFamily,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
