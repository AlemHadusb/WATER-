package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Bill
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.WaterViewModel

@Composable
fun BillsScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    val bills by viewModel.bills.collectAsState()
    val searchQuery by viewModel.billSearchQuery.collectAsState()
    val statusFilter by viewModel.billStatusFilter.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var billToPay by remember { mutableStateOf<Bill?>(null) }

    val filterOptions = listOf("ALL", "UNPAID", "PARTIALLY PAID", "PAID")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.billSearchQuery.value = it },
            label = { Text("Search Bills (Customer, Meter #, Month)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.billSearchQuery.value = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("bill_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Status filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filterOptions.forEach { opt ->
                val isSelected = statusFilter == opt
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.billStatusFilter.value = opt },
                    label = { Text(opt, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Generated Bills (${bills.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (bills.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No bills matching criteria.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bills, key = { it.id }) { bill ->
                    BillCard(
                        bill = bill,
                        canPay = currentUser?.role == UserRole.ACCOUNTANT.name ||
                                 currentUser?.role == UserRole.ADMIN.name ||
                                 currentUser?.role == UserRole.DEVELOPER.name,
                        onPayClick = { billToPay = bill }
                    )
                }
            }
        }
    }

    if (billToPay != null) {
        RecordPaymentDialog(
            bill = billToPay!!,
            viewModel = viewModel,
            onDismiss = { billToPay = null }
        )
    }
}

@Composable
fun BillCard(
    bill: Bill,
    canPay: Boolean,
    onPayClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = bill.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Meter: ${bill.meterNumber} • ${bill.billMonth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WaterBluePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Payment Status Badge
                val (badgeBg, badgeText) = when (bill.paymentStatus) {
                    "PAID" -> Pair(WaterGreenPaid.copy(alpha = 0.15f), WaterGreenPaid)
                    "PARTIALLY PAID" -> Pair(WaterOrangePending.copy(alpha = 0.15f), WaterOrangePending)
                    else -> Pair(WaterRedExpired.copy(alpha = 0.15f), WaterRedExpired)
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = bill.paymentStatus,
                        color = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Readings: ${bill.previousReading.toInt()} -> ${bill.lastReading.toInt()} m³",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Consumed: ${bill.waterConsumed.toInt()} m³",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Charge: ${"%.2f".format(bill.waterCharge)} + Rent: ${"%.2f".format(bill.meterRent)} Birr",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Total: ${"%.2f".format(bill.totalPayable)} Birr",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = WaterBluePrimary
                )
            }

            val remaining = (bill.totalPayable - bill.amountPaid).coerceAtLeast(0.0)
            if (bill.amountPaid > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Paid: ${"%.2f".format(bill.amountPaid)} Birr",
                        fontSize = 12.sp,
                        color = WaterGreenPaid,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Remaining: ${"%.2f".format(remaining)} Birr",
                        fontSize = 12.sp,
                        color = if (remaining > 0) WaterOrangePending else WaterGreenPaid,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (canPay && bill.paymentStatus != "PAID") {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onPayClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterGreenPaid),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Record Payment", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
