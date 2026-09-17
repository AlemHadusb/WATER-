package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Bill
import com.example.data.model.Payment
import com.example.ui.components.ReceiptDialog
import com.example.ui.theme.WaterBlueLight
import com.example.ui.theme.WaterBluePrimary
import com.example.ui.theme.WaterGreenPaid
import com.example.ui.theme.WaterOrangePending
import com.example.viewmodel.WaterViewModel

@Composable
fun PaymentScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    val payments by viewModel.payments.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val activeReceipt by viewModel.activeReceiptPayment.collectAsState()
    val appName by viewModel.appName.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Record Payment, 1: Payment Records
    var billToPay by remember { mutableStateOf<Bill?>(null) }
    var searchUnpaidQuery by remember { mutableStateOf("") }

    val unpaidBills = remember(bills, searchUnpaidQuery) {
        bills.filter { it.paymentStatus != "PAID" }.filter { b ->
            searchUnpaidQuery.isBlank() ||
                    b.customerName.contains(searchUnpaidQuery, ignoreCase = true) ||
                    b.meterNumber.contains(searchUnpaidQuery, ignoreCase = true) ||
                    b.billMonth.contains(searchUnpaidQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Collect Payment", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.AddCard, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Receipt History (${payments.size})", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            // Unpaid bills collection list
            OutlinedTextField(
                value = searchUnpaidQuery,
                onValueChange = { searchUnpaidQuery = it },
                label = { Text("Search Unpaid Bills (Name, Meter, Month)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("search_unpaid_bills")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Unpaid & Partially Paid Bills (${unpaidBills.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (unpaidBills.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No pending unpaid bills found. All clear!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(unpaidBills, key = { it.id }) { bill ->
                        val remaining = (bill.totalPayable - bill.amountPaid).coerceAtLeast(0.0)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
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
                                        text = bill.customerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Meter: ${bill.meterNumber} • ${bill.billMonth}",
                                        fontSize = 12.sp,
                                        color = WaterBluePrimary
                                    )
                                    Text(
                                        text = "Due: ${"%.2f".format(remaining)} Birr (Total: ${"%.2f".format(bill.totalPayable)})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (bill.paymentStatus == "PARTIALLY PAID") WaterOrangePending else MaterialTheme.colorScheme.error
                                    )
                                }

                                Button(
                                    onClick = { billToPay = bill },
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterGreenPaid),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Pay", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Receipt history list
            if (payments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No payments recorded yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(payments, key = { it.id }) { payment ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.showReceipt(payment) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = payment.receiptNumber,
                                        fontWeight = FontWeight.Black,
                                        color = WaterBluePrimary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${"%.2f".format(payment.amountPaid)} Birr",
                                        fontWeight = FontWeight.Black,
                                        color = WaterGreenPaid,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "${payment.customerName} • Meter: ${payment.meterNumber} (${payment.billMonth})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Date: ${payment.paymentDate} • Accountant: ${payment.accountantName}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { viewModel.showReceipt(payment) }) {
                                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("View Receipt", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Payment Input Dialog
    if (billToPay != null) {
        RecordPaymentDialog(
            bill = billToPay!!,
            viewModel = viewModel,
            onDismiss = { billToPay = null }
        )
    }

    // Official Receipt Modal
    if (activeReceipt != null) {
        ReceiptDialog(
            payment = activeReceipt!!,
            appName = appName,
            onDismiss = { viewModel.dismissReceipt() }
        )
    }
}

@Composable
fun RecordPaymentDialog(
    bill: Bill,
    viewModel: WaterViewModel,
    onDismiss: () -> Unit
) {
    val remainingBalance = (bill.totalPayable - bill.amountPaid).coerceAtLeast(0.0)
    var amountInput by remember { mutableStateOf(remainingBalance.toString()) }
    var inputError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Record Water Bill Payment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WaterBluePrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = WaterBlueLight,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = bill.customerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "Meter: ${bill.meterNumber} • Month: ${bill.billMonth}", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remaining Due: ${"%.2f".format(remainingBalance)} Birr",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = WaterBluePrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (inputError != null) {
                    Text(
                        text = inputError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        inputError = null
                    },
                    label = { Text("Payment Amount (Birr)") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = { amountInput = remainingBalance.toString() }) {
                            Text("Full Pay", fontSize = 11.sp)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountInput.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                inputError = "Please enter a valid amount greater than zero."
                                return@Button
                            }
                            if (amount > remainingBalance + 0.01) {
                                inputError = "Amount cannot exceed remaining balance (${remainingBalance} Birr)."
                                return@Button
                            }
                            viewModel.recordPayment(bill.id, amount) {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterGreenPaid),
                        modifier = Modifier.testTag("submit_payment_button")
                    ) {
                        Text("Confirm & Print Receipt")
                    }
                }
            }
        }
    }
}
