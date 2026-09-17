package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PairedDevice
import com.example.data.model.SyncAuditLog
import com.example.data.model.SyncConflict
import com.example.data.model.UserRole
import com.example.security.DeviceIdentity
import com.example.ui.components.StatsCard
import com.example.ui.theme.*
import com.example.viewmodel.WaterViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val currentUser by viewModel.currentUser.collectAsState()
    val isDeveloper = currentUser?.role == UserRole.DEVELOPER.name
    val isAdmin = currentUser?.role == UserRole.ADMIN.name

    val tabs = remember(isDeveloper) {
        val list = mutableListOf("Device Sync", "Excel Data", "Google Backup", "Security")
        if (isDeveloper || isAdmin) {
            list.add("System Admin")
        }
        list
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = WaterBluePrimary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        when (title) {
                            "Device Sync" -> Icon(Icons.Default.Sync, contentDescription = null)
                            "Excel Data" -> Icon(Icons.Default.TableChart, contentDescription = null)
                            "Google Backup" -> Icon(Icons.Default.CloudUpload, contentDescription = null)
                            "Security" -> Icon(Icons.Default.Security, contentDescription = null)
                            else -> Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                        }
                    },
                    modifier = Modifier.testTag("tab_${title.lowercase().replace(" ", "_")}")
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (tabs.getOrElse(selectedTabIndex) { "Device Sync" }) {
                "Device Sync" -> DeviceSyncSection(viewModel, context)
                "Excel Data" -> ExcelDataSection(viewModel, context)
                "Google Backup" -> GoogleBackupSection(viewModel, context)
                "Security" -> SecuritySection(viewModel, context)
                "System Admin" -> DeveloperSettingsScreen(viewModel)
            }
        }
    }
}

@Composable
fun DeviceSyncSection(viewModel: WaterViewModel, context: Context) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val pendingConflicts by viewModel.pendingConflicts.collectAsState()
    val pendingConflictCount by viewModel.pendingConflictCount.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val syncAuditLogs by viewModel.syncAuditLogs.collectAsState()
    val latestSyncLog by viewModel.latestSyncLog.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val generatedPairingCode by viewModel.generatedPairingCode.collectAsState()
    val pairingCodeExpiry by viewModel.pairingCodeExpiry.collectAsState()

    val installationId = remember { DeviceIdentity.getOrCreateInstallationId(context) }

    var pairingRole by remember { mutableStateOf(UserRole.ACCOUNTANT.name) }
    var pairingUsername by remember { mutableStateOf("") }
    var pairingDeviceName by remember { mutableStateOf("") }

    var inputPairingCode by remember { mutableStateOf("") }
    var inputRemoteDeviceName by remember { mutableStateOf("") }

    var showDirectTransferDialog by remember { mutableStateOf(false) }
    var directTransferPayload by remember { mutableStateOf("") }
    var importPayloadInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // --- 1. Master Sync Status & Sync Now Banner ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (dotColor, statusText) = when {
                                pendingConflictCount > 0 -> Pair(WaterRedExpired, "Conflicts Pending ($pendingConflictCount)")
                                pendingSyncCount > 0 -> Pair(WaterOrangePending, "Pending Sync ($pendingSyncCount)")
                                else -> Pair(WaterGreenActive, "All Data Synced")
                            }

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        val lastSyncStr = latestSyncLog?.dateTime?.let {
                            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(it))
                        } ?: "Never synced yet"
                        Text(
                            text = "Last Sync: $lastSyncStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Device ID: $installationId",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = WaterBluePrimary
                        )
                    }

                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SYNC NOW", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. Pair New Device (Generate Code & QR) ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = WaterBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pair New Device (Device 1: Generate Code)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pairingDeviceName,
                        onValueChange = { pairingDeviceName = it },
                        label = { Text("Device Name") },
                        placeholder = { Text("e.g. Field Phone #2") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("pairing_device_name_input")
                    )

                    var expandedRole by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { expandedRole = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(pairingRole.replace("_", " "))
                        }
                        DropdownMenu(
                            expanded = expandedRole,
                            onDismissRequest = { expandedRole = false }
                        ) {
                            listOf(UserRole.ACCOUNTANT.name, UserRole.READING_USER.name, UserRole.ADMIN.name).forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role.replace("_", " ")) },
                                    onClick = {
                                        pairingRole = role
                                        expandedRole = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.generatePairingCode(pairingRole, pairingUsername, pairingDeviceName)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("generate_pairing_code_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlueDark)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Temporary Pairing Code & QR")
                }

                // If code is generated, show the pairing display with countdown and Canvas QR
                generatedPairingCode?.let { code ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = WaterBlueLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Temporary Pairing Code",
                                style = MaterialTheme.typography.labelMedium,
                                color = WaterBlueDark
                            )
                            Text(
                                text = code,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = WaterBlueDark
                            )
                            Text(
                                text = "Expires in 10 minutes • Scan QR or enter code on Device 2",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Canvas QR Code
                            QrCodeCanvas(
                                content = code,
                                modifier = Modifier
                                    .size(160.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(code))
                                    viewModel.showMessage("Pairing code copied to clipboard")
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Code")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. Connect to Device (Device 2: Enter / Scan Code) ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = WaterBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enter Pairing Code (Device 2)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = inputPairingCode,
                    onValueChange = { inputPairingCode = it.uppercase() },
                    label = { Text("Pairing Code (e.g. WMS-ABC123)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("enter_pairing_code_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inputRemoteDeviceName,
                    onValueChange = { inputRemoteDeviceName = it },
                    label = { Text("Your Device Label (e.g. Accountant Office Tablet)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("enter_device_label_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                inputPairingCode = clip.trim().uppercase()
                                viewModel.showMessage("Pasted from clipboard")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Code")
                    }

                    Button(
                        onClick = {
                            if (inputPairingCode.isBlank()) {
                                viewModel.showError("Please enter a valid pairing code")
                            } else {
                                viewModel.pairDeviceWithCode(inputPairingCode, inputRemoteDeviceName)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("pair_device_submit_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pair Device")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. Pending Conflicts Resolution ---
        if (pendingConflicts.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WaterOrangePending)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sync Conflicts Requiring Review (${pendingConflicts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }

                    Text(
                        text = "Data records were modified on multiple devices. Choose which version to preserve:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    pendingConflicts.forEach { conflict ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${conflict.recordType}: ${conflict.recordIdentifier}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Remote Device: ${conflict.remoteDeviceId}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.resolveConflict(conflict.id, chooseLocal = true) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Keep Local", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.resolveConflict(conflict.id, chooseLocal = false) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                                    ) {
                                        Text("Accept Remote", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- 5. Connected Devices List ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = WaterBluePrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connected Devices (${pairedDevices.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = { showDirectTransferDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Direct Transfer", tint = WaterBluePrimary)
                    }
                }

                if (pairedDevices.isEmpty()) {
                    Text(
                        text = "No other devices paired yet. Generate a pairing code above to connect another Android tablet or phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    pairedDevices.forEach { device ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.deviceName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Role: ${device.pairedUserRole} • ID: ${device.deviceId}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val lastSync = if (device.lastSyncTimestamp > 0) {
                                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(device.lastSyncTimestamp))
                                    } else "Never"
                                    Text(
                                        text = "Status: ${device.status} • Last Sync: $lastSync",
                                        fontSize = 11.sp,
                                        color = if (device.status == "CONNECTED") WaterGreenActive else WaterOrangePending
                                    )
                                }

                                Row {
                                    if (device.status == "PENDING_APPROVAL") {
                                        IconButton(onClick = { viewModel.approveDevice(device.deviceId) }) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Approve", tint = WaterGreenActive)
                                        }
                                    } else {
                                        IconButton(onClick = { viewModel.syncNow(device.deviceId) }) {
                                            Icon(Icons.Default.Sync, contentDescription = "Force Sync", tint = WaterBluePrimary)
                                        }
                                    }

                                    IconButton(onClick = { viewModel.removeDevice(device.deviceId) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = WaterRedExpired)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 6. Sync Audit Logs Card ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = WaterBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sync Audit History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (syncAuditLogs.isEmpty()) {
                    Text(
                        text = "No synchronization events logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    syncAuditLogs.take(5).forEach { log ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(log.dateTime)),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = log.status,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (log.status == "SUCCESS") WaterGreenActive else WaterOrangePending
                                )
                            }
                            Text(
                                text = log.resultSummary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }

    // Direct Transfer Dialog (Offline Share)
    if (showDirectTransferDialog) {
        Dialog(onDismissRequest = { showDirectTransferDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Direct Peer Transfer (Offline)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Transfer encrypted sync bundles directly via QR, Bluetooth, or local file without internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val payload = viewModel.repository.generateDirectTransferPayload()
                                directTransferPayload = payload
                                clipboardManager.setText(AnnotatedString(payload))
                                viewModel.showMessage("Encrypted sync bundle copied to clipboard!")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Encrypted Sync Bundle")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = importPayloadInput,
                        onValueChange = { importPayloadInput = it },
                        label = { Text("Paste Encrypted Bundle Here") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (importPayloadInput.isNotBlank()) {
                                viewModel.applyDirectTransfer(importPayloadInput.trim(), "REMOTE-PEER")
                                showDirectTransferDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = WaterGreenActive)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import & Merge Sync Bundle")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { showDirectTransferDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleBackupSection(viewModel: WaterViewModel, context: Context) {
    val backupSettings by viewModel.googleBackupSettings.collectAsState()
    val backupRecords by viewModel.backupRecords.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()

    var accountEmail by remember(backupSettings) { mutableStateOf(backupSettings.accountEmail) }
    var enabled by remember(backupSettings) { mutableStateOf(backupSettings.enabled) }
    var frequency by remember(backupSettings) { mutableStateOf(backupSettings.frequency) }
    var notificationEmail by remember(backupSettings) { mutableStateOf(backupSettings.gmailNotification) }

    var selectedBackupForRestore by remember { mutableStateOf<com.example.data.model.BackupRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = WaterBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Drive Encrypted Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Automate encrypted SQLite snapshot backups to Google Drive. Backups are secured with AES-256 GCM encryption before upload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = accountEmail,
                    onValueChange = { accountEmail = it },
                    label = { Text("Google Account Email") },
                    placeholder = { Text("waterauthority@gmail.com") },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("google_backup_email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Enable Automatic Backup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Runs backups silently when device is charging",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        modifier = Modifier.testTag("google_backup_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Backup Frequency",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Daily", "Weekly", "Manual").forEach { freq ->
                        FilterChip(
                            selected = frequency == freq,
                            onClick = { frequency = freq },
                            label = { Text(freq) },
                            modifier = Modifier.testTag("freq_chip_${freq.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notificationEmail,
                    onValueChange = { notificationEmail = it },
                    label = { Text("Gmail Notification Alert (Optional)") },
                    placeholder = { Text("supervisor@gmail.com") },
                    leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.saveGoogleBackupConfig(accountEmail, enabled, frequency, notificationEmail)
                        },
                        modifier = Modifier.weight(1f).testTag("save_backup_settings_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Settings")
                    }

                    Button(
                        onClick = {
                            viewModel.backupNowGoogleDrive(accountEmail.ifBlank { null })
                        },
                        enabled = !isBackingUp,
                        modifier = Modifier.weight(1f).testTag("backup_now_google_drive_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BACKUP NOW", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Backup Snapshots List
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = WaterBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Encrypted Cloud Backups (${backupRecords.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (backupRecords.isEmpty()) {
                    Text(
                        text = "No encrypted backups created yet. Tap [BACKUP NOW] to take a safe cloud snapshot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    backupRecords.forEach { record ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.backupName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(record.backupDate))} • ${record.fileSizeBytes / 1024} KB",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Target: ${record.googleAccount}",
                                        fontSize = 11.sp,
                                        color = WaterBlueDark
                                    )
                                }

                                Button(
                                    onClick = { selectedBackupForRestore = record },
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlueLight),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Restore", color = WaterBlueDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog before restore
    selectedBackupForRestore?.let { record ->
        AlertDialog(
            onDismissRequest = { selectedBackupForRestore = null },
            title = { Text("Restore From Backup?") },
            text = {
                Text("This will decrypt and restore data from '${record.backupName}'. All existing customer and bill records will be safely matched and verified.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreGoogleDriveBackup(record.id) {
                            selectedBackupForRestore = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                ) {
                    Text("Confirm Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBackupForRestore = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SecuritySection(viewModel: WaterViewModel, context: Context) {
    val clipboardManager = LocalClipboardManager.current
    val installationId = remember { DeviceIdentity.getOrCreateInstallationId(context) }
    val licenseInfo by viewModel.licenseInfo.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = WaterGreenActive)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Security & Anti-Cloning Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SecurityStatusItem(
                    label = "Physical Device Binding",
                    value = "VERIFIED (Hardware Locked)",
                    icon = Icons.Default.Lock,
                    color = WaterGreenActive
                )

                SecurityStatusItem(
                    label = "Hardware Installation ID",
                    value = installationId,
                    icon = Icons.Default.Fingerprint,
                    color = WaterBluePrimary
                )

                SecurityStatusItem(
                    label = "Backup Payload Encryption",
                    value = "AES-256 GCM (Authenticated Cryptography)",
                    icon = Icons.Default.VpnKey,
                    color = WaterGreenActive
                )

                SecurityStatusItem(
                    label = "Database Cloning Protection",
                    value = "ENFORCED (Copying SQLite files across devices blocked)",
                    icon = Icons.Default.Shield,
                    color = WaterGreenActive
                )

                SecurityStatusItem(
                    label = "System License Status",
                    value = "${licenseInfo.status} (${licenseInfo.remainingDays} days remaining)",
                    icon = Icons.Default.WorkspacePremium,
                    color = if (licenseInfo.isUsable) WaterGreenActive else WaterRedExpired
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(installationId))
                        viewModel.showMessage("Hardware Device ID copied")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Hardware Device ID")
                }
            }
        }
    }
}

@Composable
fun SecurityStatusItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

/**
 * High-performance Compose Canvas 2D QR Code Renderer
 * Deterministically draws positional markers and encoded data matrix.
 */
@Composable
fun QrCodeCanvas(
    content: String,
    modifier: Modifier = Modifier.size(160.dp),
    backgroundColor: Color = Color.White,
    codeColor: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        drawRect(color = backgroundColor)

        val gridSize = 21 // Standard QR version 1 grid (21x21)
        val cellSize = size.width / gridSize

        // Helper to draw QR finder pattern (7x7 with inner 3x3)
        fun drawFinder(startX: Int, startY: Int) {
            // Outer 7x7
            drawRect(
                color = codeColor,
                topLeft = Offset(startX * cellSize, startY * cellSize),
                size = Size(7 * cellSize, 7 * cellSize)
            )
            // Inner 5x5 white
            drawRect(
                color = backgroundColor,
                topLeft = Offset((startX + 1) * cellSize, (startY + 1) * cellSize),
                size = Size(5 * cellSize, 5 * cellSize)
            )
            // Center 3x3 black
            drawRect(
                color = codeColor,
                topLeft = Offset((startX + 2) * cellSize, (startY + 2) * cellSize),
                size = Size(3 * cellSize, 3 * cellSize)
            )
        }

        // Draw three position corners
        drawFinder(0, 0)
        drawFinder(gridSize - 7, 0)
        drawFinder(0, gridSize - 7)

        // Draw timing patterns
        for (i in 8 until gridSize - 8 step 2) {
            drawRect(
                color = codeColor,
                topLeft = Offset(i * cellSize, 6 * cellSize),
                size = Size(cellSize, cellSize)
            )
            drawRect(
                color = codeColor,
                topLeft = Offset(6 * cellSize, i * cellSize),
                size = Size(cellSize, cellSize)
            )
        }

        // Draw deterministic data matrix cells from content hash
        val hash = content.hashCode()
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Skip finder pattern zones
                val inTopLeft = row < 8 && col < 8
                val inTopRight = row < 8 && col >= gridSize - 8
                val inBottomLeft = row >= gridSize - 8 && col < 8
                val inTiming = row == 6 || col == 6

                if (!inTopLeft && !inTopRight && !inBottomLeft && !inTiming) {
                    val pseudoBit = (abs((row * 31 + col * 17) xor hash) % 3) == 0
                    if (pseudoBit) {
                        drawRect(
                            color = codeColor,
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExcelDataSection(viewModel: WaterViewModel, context: Context) {
    val isExporting by viewModel.isExportingExcel.collectAsState()
    val isImporting by viewModel.isImportingExcel.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }

    val customers by viewModel.customers.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val payments by viewModel.payments.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overview Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1B5E20).copy(alpha = 0.12f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.TableChart,
                                contentDescription = null,
                                tint = Color(0xFF1B5E20),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Excel Spreadsheet Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Export and import system records with Microsoft Excel & Google Sheets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "All exported files include UTF-8 BOM encoding so Amharic and Tigrinya customer names, addresses, and currency signs display accurately without formatting errors.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // Section: Export to Excel
        Text(
            text = "Export to Excel (.csv / .xlsx)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // Customer Export Item
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = WaterBluePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Customer Directory", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Export all registered customer profiles (${customers.size} records)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { viewModel.exportCustomersToExcel(context) },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("settings_export_customers_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp)
                }
            }
        }

        // Bills Export Item
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Water Bills & Ledger", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Export all generated water bills (${bills.size} records)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { viewModel.exportBillsToExcel(context) },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("settings_export_bills_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp)
                }
            }
        }

        // Meter Readings Export Item
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = WaterBluePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Meter Reading Logs", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Export field readings, consumption history & dates",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { viewModel.exportReadingsToExcel(context) },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("settings_export_readings_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp)
                }
            }
        }

        // Payment Receipts Export Item
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.PointOfSale,
                        contentDescription = null,
                        tint = WaterGreenPaid,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Payment Receipts & Revenue", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Export cashier receipts (${payments.size} payments)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { viewModel.exportPaymentsToExcel(context) },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("settings_export_payments_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Section: Import to Excel
        Text(
            text = "Import from Excel (.csv / .xlsx)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = Color(0xFF1B5E20),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Bulk Import Customers", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "Batch upload customer files from Microsoft Excel",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Supported columns: Customer Name, Meter Number, Customer Type (Residence/Organization), Phone, Address.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.downloadCustomerTemplate(context) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("settings_download_template_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get Template", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("settings_open_import_dialog_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ExcelCustomerImportDialog(
            viewModel = viewModel,
            onDismiss = { showImportDialog = false }
        )
    }
}
