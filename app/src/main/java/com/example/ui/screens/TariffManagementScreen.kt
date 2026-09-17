package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.example.data.model.CustomerType
import com.example.data.model.Tariff
import com.example.ui.theme.WaterBlueLight
import com.example.ui.theme.WaterBluePrimary
import com.example.ui.theme.WaterCyanSecondary
import com.example.viewmodel.WaterViewModel

@Composable
fun TariffManagementScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    val allTariffs by viewModel.allTariffs.collectAsState()
    val allMeterRents by viewModel.allMeterRents.collectAsState()

    var selectedType by remember { mutableStateOf(CustomerType.RESIDENCE) }
    var showAddDialog by remember { mutableStateOf(false) }
    var tariffToEdit by remember { mutableStateOf<Tariff?>(null) }
    var showMeterRentDialog by remember { mutableStateOf(false) }

    val filteredTariffs = remember(allTariffs, selectedType) {
        allTariffs.filter { it.customerType == selectedType.name }
    }

    val currentRent = remember(allMeterRents, selectedType) {
        allMeterRents.firstOrNull { it.customerType == selectedType.name }?.amount ?: (if (selectedType == CustomerType.RESIDENCE) 30.0 else 60.0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Customer Type Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = selectedType == CustomerType.RESIDENCE,
                onClick = { selectedType = CustomerType.RESIDENCE },
                label = { Text("መኖርያ (Residence)", fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    if (selectedType == CustomerType.RESIDENCE) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = selectedType == CustomerType.ORGANIZATION,
                onClick = { selectedType = CustomerType.ORGANIZATION },
                label = { Text("ድርጅት (Organization)", fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    if (selectedType == CustomerType.ORGANIZATION) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Meter Rent Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = WaterBlueLight),
            modifier = Modifier.fillMaxWidth()
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
                        text = "Monthly Meter Rent (${selectedType.displayName})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${"%.2f".format(currentRent)} Birr / month",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = WaterBluePrimary
                    )
                }

                Button(
                    onClick = { showMeterRentDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tariff Tiers Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Consumption Tiers (${filteredTariffs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Historical bills retain their locked snapshot prices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = WaterCyanSecondary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Tier")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tiers List
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredTariffs, key = { it.id }) { tariff ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val rangeText = if (tariff.maxUsage >= 90000) {
                                "> ${tariff.minUsage.toInt()} m³"
                            } else {
                                "${tariff.minUsage.toInt()} - ${tariff.maxUsage.toInt()} m³"
                            }
                            Text(
                                text = rangeText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Effective: ${tariff.effectiveDate}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${"%.2f".format(tariff.price)} Birr/m³",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = WaterBluePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { tariffToEdit = tariff }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteTariff(tariff) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Tariff Dialog
    if (showAddDialog) {
        TariffDialog(
            customerType = selectedType,
            initialTariff = null,
            onDismiss = { showAddDialog = false },
            onSave = { min, max, price ->
                viewModel.addTariff(selectedType, min, max, price) {
                    showAddDialog = false
                }
            }
        )
    }

    // Edit Tariff Dialog
    if (tariffToEdit != null) {
        TariffDialog(
            customerType = selectedType,
            initialTariff = tariffToEdit,
            onDismiss = { tariffToEdit = null },
            onSave = { min, max, price ->
                val updated = tariffToEdit!!.copy(minUsage = min, maxUsage = max, price = price)
                viewModel.updateTariff(updated) {
                    tariffToEdit = null
                }
            }
        )
    }

    // Meter Rent Dialog
    if (showMeterRentDialog) {
        var rentInput by remember { mutableStateOf(currentRent.toString()) }
        Dialog(onDismissRequest = { showMeterRentDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Update Meter Rent (${selectedType.displayName})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rentInput,
                        onValueChange = { rentInput = it },
                        label = { Text("Monthly Rent (Birr)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showMeterRentDialog = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val amount = rentInput.toDoubleOrNull() ?: return@Button
                                viewModel.updateMeterRent(selectedType, amount)
                                showMeterRentDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TariffDialog(
    customerType: CustomerType,
    initialTariff: Tariff?,
    onDismiss: () -> Unit,
    onSave: (min: Double, max: Double, price: Double) -> Unit
) {
    var minInput by remember { mutableStateOf(initialTariff?.minUsage?.toString() ?: "") }
    var maxInput by remember { mutableStateOf(initialTariff?.maxUsage?.toString() ?: "") }
    var priceInput by remember { mutableStateOf(initialTariff?.price?.toString() ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (initialTariff == null) "Add Tariff Tier" else "Edit Tariff Tier",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WaterBluePrimary
                )
                Text(
                    text = "Customer Type: ${customerType.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (errorMsg != null) {
                    Text(text = errorMsg ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minInput,
                        onValueChange = { minInput = it; errorMsg = null },
                        label = { Text("Min (m³)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxInput,
                        onValueChange = { maxInput = it; errorMsg = null },
                        label = { Text("Max (m³)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { priceInput = it; errorMsg = null },
                    label = { Text("Price per m³ (Birr)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val min = minInput.toDoubleOrNull()
                            val max = maxInput.toDoubleOrNull()
                            val price = priceInput.toDoubleOrNull()
                            if (min == null || max == null || price == null) {
                                errorMsg = "Please fill all fields with valid numbers."
                                return@Button
                            }
                            if (min > max) {
                                errorMsg = "Minimum usage cannot exceed maximum usage."
                                return@Button
                            }
                            onSave(min, max, price)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                    ) {
                        Text("Save Tier")
                    }
                }
            }
        }
    }
}
