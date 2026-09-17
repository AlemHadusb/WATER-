package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.components.StatsCard
import com.example.ui.theme.*
import com.example.viewmodel.WaterViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeveloperSettingsScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val appName by viewModel.appName.collectAsState()
    val licenseInfo by viewModel.licenseInfo.collectAsState()
    val recentLogs by viewModel.recentAuditLogs.collectAsState()

    var newAppNameInput by remember(appName) { mutableStateOf(appName) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "System Administration & Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Developer Control Panel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Application Name Configuration Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = WaterBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Organization / System Name",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newAppNameInput,
                    onValueChange = { newAppNameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("app_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.updateApplicationName(newAppNameInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                    modifier = Modifier.align(Alignment.End).testTag("save_app_name_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Update Name")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. License Management Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = WaterCyanSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "License Management",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = when (licenseInfo.status) {
                            "ACTIVE" -> WaterGreenPaid.copy(alpha = 0.15f)
                            "EXPIRING_SOON" -> WaterOrangePending.copy(alpha = 0.15f)
                            else -> WaterRedExpired.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = licenseInfo.status,
                            color = when (licenseInfo.status) {
                                "ACTIVE" -> WaterGreenPaid
                                "EXPIRING_SOON" -> WaterOrangePending
                                else -> WaterRedExpired
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Remaining Validity: ${licenseInfo.remainingDays} Days",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhonelinkLock, contentDescription = null, tint = WaterBluePrimary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Hardware Installation ID (Locked):",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = viewModel.repository.installationId,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterBluePrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(viewModel.repository.installationId))
                                viewModel.showMessage("Hardware Device ID copied to clipboard")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Hardware ID",
                                tint = WaterBluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                licenseInfo.license?.let { lic ->
                    Text(
                        text = "Current License Plan: ${lic.licenseType} (Expires: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(lic.expiryDate))})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showLicenseDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterCyanSecondary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Renew / Extend License")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Backup & Restore Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = WaterTeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Database Backup & Restore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Export local JSON database backup or restore data locally without requiring internet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val json = viewModel.repository.exportDatabaseBackupJson()
                                clipboardManager.setText(AnnotatedString(json))
                                viewModel.showMessage("Backup exported to clipboard!")

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Water_System_Backup.json")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Save Backup JSON"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Backup", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore Data", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Audit Trail Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = Color(0xFF673AB7))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security Audit Trail",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = { showLogsDialog = true }) {
                        Text("View All (${recentLogs.size})")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Logs user actions, logins, payments, and system changes for accountability.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Danger Zone: Reset Application
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset Application Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Permanently clears all customers, bills, readings, and reset the system to initial factory state.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset All System Data")
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // License Renewal Dialog
    if (showLicenseDialog) {
        val options = listOf("1 Month", "3 Months", "6 Months", "9 Months", "1 Year")
        var chosen by remember { mutableStateOf("1 Year") }
        Dialog(onDismissRequest = { showLicenseDialog = false }) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Renew / Extend License", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    options.forEach { opt ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = chosen == opt, onClick = { chosen = opt })
                            Text(text = opt, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showLicenseDialog = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.activateLicense(chosen)
                                showLicenseDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                        ) {
                            Text("Confirm Activation")
                        }
                    }
                }
            }
        }
    }

    // Restore Backup Dialog
    if (showRestoreDialog) {
        var jsonText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showRestoreDialog = false }) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Restore Data from Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Paste your exported JSON backup text below:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        label = { Text("JSON Backup Content") },
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.restoreBackup(jsonText) {
                                    showRestoreDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                        ) {
                            Text("Restore")
                        }
                    }
                }
            }
        }
    }

    // Reset Data Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Confirm System Reset") },
            text = { Text("Are you sure you want to reset all database data? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllData {
                            showResetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Audit Logs Dialog
    if (showLogsDialog) {
        Dialog(onDismissRequest = { showLogsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Audit Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { showLogsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(recentLogs) { log ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = log.action, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WaterBluePrimary)
                                        Text(
                                            text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(log.dateTime)),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(text = log.description, fontSize = 11.sp)
                                    Text(text = "User: ${log.username}", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
