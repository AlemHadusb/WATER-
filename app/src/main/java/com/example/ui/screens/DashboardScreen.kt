package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserRole
import com.example.ui.components.StatsCard
import com.example.ui.theme.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.WaterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val totalCustomers by viewModel.totalCustomersCount.collectAsState()
    val activeCustomers by viewModel.activeCustomersCount.collectAsState()

    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val pendingConflictCount by viewModel.pendingConflictCount.collectAsState()
    val latestSyncLog by viewModel.latestSyncLog.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val currentMonth = viewModel.currentMonthFormatted
    val todayDate = viewModel.currentDateFormatted

    val todayReadings by viewModel.repository.getTodayReadingsCount(todayDate)
        .collectAsState(initial = 0)
    val billsThisMonth by viewModel.repository.getBillsCountThisMonth(currentMonth)
        .collectAsState(initial = 0)
    val paidBills by viewModel.repository.getPaidBillsCount()
        .collectAsState(initial = 0)
    val unpaidBills by viewModel.repository.getUnpaidBillsCount()
        .collectAsState(initial = 0)
    val todayCollection by viewModel.repository.getDailyCollection(todayDate)
        .collectAsState(initial = 0.0)
    val monthlyCollection by viewModel.repository.getTotalCollectedThisMonth(currentMonth)
        .collectAsState(initial = 0.0)
    val monthlyBilled by viewModel.repository.getTotalBilledThisMonth(currentMonth)
        .collectAsState(initial = 0.0)

    val role = currentUser?.role

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Welcome banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = WaterBlueLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Water System Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlueDark
                    )
                    Text(
                        text = "Month: $currentMonth • Date: $todayDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = WaterBluePrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Synchronization Status Indicator (Requirement 47)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth().testTag("sync_status_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    val (dotColor, statusTitle) = when {
                        pendingConflictCount > 0 -> Pair(WaterRedExpired, "Sync Conflict ($pendingConflictCount)")
                        pendingSyncCount > 0 -> Pair(WaterOrangePending, "Pending Sync ($pendingSyncCount)")
                        else -> Pair(WaterGreenActive, "Synced")
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = statusTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val lastSyncText = latestSyncLog?.dateTime?.let {
                            "Last Sync: " + SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(it))
                        } ?: "Last Sync: Never"
                        Text(
                            text = lastSyncText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { viewModel.syncNow() },
                    enabled = !isSyncing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                    modifier = Modifier.testTag("dashboard_sync_now_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Syncing", fontSize = 12.sp)
                    } else {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SYNC NOW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "System Metrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Grid of Stats Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsCard(
                title = "Total Customers",
                value = "$totalCustomers",
                icon = Icons.Default.People,
                color = WaterBluePrimary,
                subtitle = "$activeCustomers Active",
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Today's Readings",
                value = "$todayReadings",
                icon = Icons.Default.Speed,
                color = WaterCyanSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsCard(
                title = "Bills This Month",
                value = "$billsThisMonth",
                icon = Icons.Default.ReceiptLong,
                color = Color(0xFF673AB7),
                subtitle = "${"%.0f".format(monthlyBilled)} Birr",
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Paid Bills",
                value = "$paidBills",
                icon = Icons.Default.CheckCircle,
                color = WaterGreenPaid,
                subtitle = "$unpaidBills Unpaid",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsCard(
                title = "Today's Collection",
                value = "${"%.2f".format(todayCollection)} Birr",
                icon = Icons.Default.Paid,
                color = WaterGreenPaid,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Monthly Collection",
                value = "${"%.2f".format(monthlyCollection)} Birr",
                icon = Icons.Default.AccountBalanceWallet,
                color = WaterTeal,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Action Buttons
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Customer Management (Admin, Developer)
            if (role == UserRole.ADMIN.name || role == UserRole.DEVELOPER.name) {
                Button(
                    onClick = { viewModel.navigateTo(AppScreen.CUSTOMERS) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("nav_customers_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Customer Management & Search", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Meter Reading (Reading User, Admin, Developer)
            if (role == UserRole.READING_USER.name || role == UserRole.ADMIN.name || role == UserRole.DEVELOPER.name) {
                Button(
                    onClick = { viewModel.navigateTo(AppScreen.READINGS) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterCyanSecondary),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("nav_readings_button")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Enter Meter Reading & Calculate Bill", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Bills & Payments (Accountant, Admin, Developer)
            if (role == UserRole.ACCOUNTANT.name || role == UserRole.ADMIN.name || role == UserRole.DEVELOPER.name) {
                Button(
                    onClick = { viewModel.navigateTo(AppScreen.PAYMENTS) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterGreenPaid),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("nav_payments_button")
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Record Payment & Print Receipt", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { viewModel.navigateTo(AppScreen.BILLS) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("nav_bills_button")
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("View & Search All Bills", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Tariffs & Meter Rent (Admin, Developer)
            if (role == UserRole.ADMIN.name || role == UserRole.DEVELOPER.name) {
                OutlinedButton(
                    onClick = { viewModel.navigateTo(AppScreen.TARIFFS) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("nav_tariffs_button")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Manage Tariffs & Meter Rent", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Reports (Admin, Accountant, Developer)
            if (role == UserRole.ACCOUNTANT.name || role == UserRole.ADMIN.name || role == UserRole.DEVELOPER.name) {
                OutlinedButton(
                    onClick = { viewModel.navigateTo(AppScreen.REPORTS) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("nav_reports_button")
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Financial & Water Reports", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
