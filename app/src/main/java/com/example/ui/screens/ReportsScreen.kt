package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.StatsCard
import com.example.ui.theme.*
import com.example.viewmodel.WaterViewModel

@Composable
fun ReportsScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentMonth = viewModel.currentMonthFormatted
    val todayDate = viewModel.currentDateFormatted
    val appName by viewModel.appName.collectAsState()

    val totalCustomers by viewModel.totalCustomersCount.collectAsState()
    val activeCustomers by viewModel.activeCustomersCount.collectAsState()
    val billsThisMonth by viewModel.repository.getBillsCountThisMonth(currentMonth).collectAsState(initial = 0)
    val monthlyBilled by viewModel.repository.getTotalBilledThisMonth(currentMonth).collectAsState(initial = 0.0)
    val monthlyCollected by viewModel.repository.getTotalCollectedThisMonth(currentMonth).collectAsState(initial = 0.0)
    val todayCollection by viewModel.repository.getDailyCollection(todayDate).collectAsState(initial = 0.0)
    val todayReadings by viewModel.repository.getTodayReadingsCount(todayDate).collectAsState(initial = 0)
    val paidBills by viewModel.repository.getPaidBillsCount().collectAsState(initial = 0)
    val unpaidBills by viewModel.repository.getUnpaidBillsCount().collectAsState(initial = 0)

    val outstandingBalance = (monthlyBilled - monthlyCollected).coerceAtLeast(0.0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Financial & Water Reports",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Summary for $currentMonth",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    shareReportSummary(
                        context = context,
                        appName = appName,
                        month = currentMonth,
                        date = todayDate,
                        totalCustomers = totalCustomers,
                        activeCustomers = activeCustomers,
                        todayReadings = todayReadings,
                        todayCollection = todayCollection,
                        monthlyBilled = monthlyBilled,
                        monthlyCollected = monthlyCollected,
                        outstanding = outstandingBalance,
                        paidCount = paidBills,
                        unpaidCount = unpaidBills
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("share_report_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export / Print")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Daily summary section
        Text(
            text = "Today's Performance ($todayDate)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsCard(
                title = "Today's Revenue",
                value = "${"%.2f".format(todayCollection)} Birr",
                icon = Icons.Default.Paid,
                color = WaterGreenPaid,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Readings Taken",
                value = "$todayReadings",
                icon = Icons.Default.Speed,
                color = WaterCyanSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Monthly Summary Section
        Text(
            text = "Monthly Financial Breakdown ($currentMonth)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ReportItem(label = "Total Bills Generated:", value = "$billsThisMonth Bills")
                ReportItem(label = "Total Water Amount Billed:", value = "${"%.2f".format(monthlyBilled)} Birr", isBold = true)
                ReportItem(label = "Total Revenue Collected:", value = "${"%.2f".format(monthlyCollected)} Birr", valueColor = WaterGreenPaid, isBold = true)
                ReportItem(label = "Outstanding / Unpaid Balance:", value = "${"%.2f".format(outstandingBalance)} Birr", valueColor = WaterRedExpired, isBold = true)

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                ReportItem(label = "Fully Paid Bills:", value = "$paidBills Bills", valueColor = WaterGreenPaid)
                ReportItem(label = "Pending / Unpaid Bills:", value = "$unpaidBills Bills", valueColor = WaterOrangePending)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Customer Statistics Section
        Text(
            text = "Customer Distribution",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ReportItem(label = "Total Registered Customers:", value = "$totalCustomers")
                ReportItem(label = "Active Operational Customers:", value = "$activeCustomers")
                ReportItem(label = "Deactivated Customers:", value = "${(totalCustomers - activeCustomers).coerceAtLeast(0)}")
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun ReportItem(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}

private fun shareReportSummary(
    context: Context,
    appName: String,
    month: String,
    date: String,
    totalCustomers: Int,
    activeCustomers: Int,
    todayReadings: Int,
    todayCollection: Double,
    monthlyBilled: Double,
    monthlyCollected: Double,
    outstanding: Double,
    paidCount: Int,
    unpaidCount: Int
) {
    val report = """
        ========================================
        $appName
        FINANCIAL & WATER MANAGEMENT REPORT
        Date: $date | Month: $month
        ========================================
        1. DAILY ACTIVITY ($date)
        - Revenue Collected Today: ${"%.2f".format(todayCollection)} Birr
        - Meter Readings Taken Today: $todayReadings
        ----------------------------------------
        2. MONTHLY FINANCIAL SUMMARY ($month)
        - Total Billed: ${"%.2f".format(monthlyBilled)} Birr
        - Total Collected: ${"%.2f".format(monthlyCollected)} Birr
        - Outstanding Balance: ${"%.2f".format(outstanding)} Birr
        - Paid Bills: $paidCount
        - Unpaid Bills: $unpaidCount
        ----------------------------------------
        3. CUSTOMER BASE
        - Total Registered: $totalCustomers
        - Active Customers: $activeCustomers
        ========================================
        Generated locally by $appName
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "$appName - Report ($month)")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Export / Share Report"))
}
