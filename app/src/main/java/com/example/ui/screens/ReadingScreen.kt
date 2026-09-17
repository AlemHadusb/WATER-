package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Customer
import com.example.data.model.CustomerType
import com.example.data.repository.BillCalculationPreview
import com.example.ui.theme.*
import com.example.viewmodel.WaterViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@Composable
fun ReadingScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var previousReadingValue by remember { mutableDoubleStateOf(0.0) }
    var lastReadingInput by remember { mutableStateOf("") }
    var billMonthInput by remember { mutableStateOf(viewModel.currentMonthFormatted) }

    var calculationPreview by remember { mutableStateOf<BillCalculationPreview?>(null) }
    var calculationError by remember { mutableStateOf<String?>(null) }

    val customers by viewModel.customers.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Trigger preview calculation when last reading input changes
    LaunchedEffect(selectedCustomer, lastReadingInput) {
        val cust = selectedCustomer
        val lastVal = lastReadingInput.toDoubleOrNull()
        if (cust != null && lastVal != null) {
            viewModel.calculatePreview(cust.id, lastVal) { result ->
                result.onSuccess { preview ->
                    calculationPreview = preview
                    calculationError = null
                }.onFailure { err ->
                    calculationPreview = null
                    calculationError = err.message
                }
            }
        } else {
            calculationPreview = null
            calculationError = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Meter Reading & Bill Generation",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Search customer by Meter Number or Name, enter the new reading, and generate bill.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Step 1: Customer Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                viewModel.customerSearchQuery.value = it
            },
            label = { Text("Search Customer (Meter # or Name)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        viewModel.customerSearchQuery.value = ""
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reading_search_customer")
        )

        // Dropdown customer results if searching and none selected
        if (selectedCustomer == null && searchQuery.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    val activeMatches = customers.filter { it.active }.take(5)
                    if (activeMatches.isEmpty()) {
                        Text(
                            text = "No active customers found for '$searchQuery'",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        activeMatches.forEach { customer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCustomer = customer
                                        searchQuery = "${customer.customerName} (${customer.meterNumber})"
                                        // fetch previous reading
                                        viewModel.viewModelScopeLaunchFetchReading(customer.id) { prev ->
                                            previousReadingValue = prev
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = customer.customerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Meter: ${customer.meterNumber} • ${customer.customerType}",
                                        fontSize = 12.sp,
                                        color = WaterBluePrimary
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Step 2: Selected Customer Information Card
        if (selectedCustomer != null) {
            val cust = selectedCustomer!!
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WaterBlueLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = cust.customerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = WaterBlueDark
                            )
                            Text(
                                text = "Meter Number: ${cust.meterNumber}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WaterBluePrimary
                            )
                            Text(
                                text = "Category: ${if (cust.customerType == CustomerType.RESIDENCE.name) "መኖርያ (Residence)" else "ድርጅት (Organization)"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = {
                            selectedCustomer = null
                            lastReadingInput = ""
                            calculationPreview = null
                        }) {
                            Icon(Icons.Default.ChangeCircle, contentDescription = "Change customer", tint = WaterBluePrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = WaterBluePrimary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Approved Previous Reading:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${"%.1f".format(previousReadingValue)} m³",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlueDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3: Meter Reading Input
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Enter Last / Current Meter Reading",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Reading must be greater than or equal to previous reading (${"%.1f".format(previousReadingValue)} m³)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = lastReadingInput,
                        onValueChange = { lastReadingInput = it },
                        label = { Text("Last Reading (Current)") },
                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                        trailingIcon = { Text("m³", modifier = Modifier.padding(end = 12.dp)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("last_reading_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = billMonthInput,
                        onValueChange = { billMonthInput = it },
                        label = { Text("Billing Month") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("bill_month_input")
                    )

                    // Error warning if Last < Previous
                    if (calculationError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = calculationError ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Step 4: Automatic Calculation Preview Card
            if (calculationPreview != null) {
                val preview = calculationPreview!!
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WaterCyanSecondary),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = WaterCyanSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Automatic Bill Calculation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = WaterCyanSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        CalculationRow(
                            label = "Water Consumed:",
                            formula = "(${preview.lastReading.toInt()} - ${preview.previousReading.toInt()})",
                            value = "${preview.waterConsumed.toInt()} m³"
                        )

                        CalculationRow(
                            label = "Applicable Tariff:",
                            formula = "${preview.waterConsumed.toInt()} m³ × ${preview.unitPrice} Birr",
                            value = "${"%.2f".format(preview.waterCharge)} Birr"
                        )

                        CalculationRow(
                            label = "Meter Rent:",
                            formula = "${cust.customerType} rent",
                            value = "${"%.2f".format(preview.meterRent)} Birr"
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL PAYABLE:",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "${"%.2f".format(preview.totalPayable)} Birr",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = WaterBluePrimary
                            )
                        }

                        Text(
                            text = "* Calculated automatically from active local tariff. User modification is strictly protected.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save Reading and Generate Bill Button
                Button(
                    onClick = {
                        val lastVal = lastReadingInput.toDoubleOrNull() ?: return@Button
                        viewModel.submitReadingAndBill(
                            customerId = cust.id,
                            lastReading = lastVal,
                            billMonth = billMonthInput
                        ) {
                            // Reset form on success
                            selectedCustomer = null
                            lastReadingInput = ""
                            searchQuery = ""
                            calculationPreview = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_reading_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Reading & Generate Bill",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun CalculationRow(label: String, formula: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = formula, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// Extension helper to fetch previous reading asynchronously
fun WaterViewModel.viewModelScopeLaunchFetchReading(
    customerId: Long,
    onResult: (Double) -> Unit
) {
    viewModelScope.launch {
        val latest = repository.getLatestReading(customerId)
        onResult(latest?.lastReading ?: 0.0)
    }
}
