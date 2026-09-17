package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Customer
import com.example.data.model.CustomerType
import com.example.ui.theme.WaterBlueLight
import com.example.ui.theme.WaterBluePrimary
import com.example.ui.theme.WaterGreenPaid
import com.example.ui.theme.WaterRedExpired
import com.example.viewmodel.WaterViewModel

@Composable
fun CustomerManagementScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    val customers by viewModel.customers.collectAsState()
    val searchQuery by viewModel.customerSearchQuery.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var selectedCustomerForDetails by remember { mutableStateOf<Customer?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search & Add Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.customerSearchQuery.value = it },
                label = { Text("Search by Name, Meter #, ID, Phone") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.customerSearchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("customer_search_input")
            )

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("add_customer_button")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Register")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Customers (${customers.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (customers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) "No customers registered yet." else "No matching customers found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(customers, key = { it.id }) { customer ->
                    CustomerCard(
                        customer = customer,
                        onClick = { selectedCustomerForDetails = customer },
                        onEdit = { customerToEdit = customer },
                        onToggleStatus = { viewModel.toggleCustomerActive(customer) }
                    )
                }
            }
        }
    }

    // Register Customer Dialog
    if (showAddDialog) {
        CustomerDialog(
            initialCustomer = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, meter, type, phone, address ->
                viewModel.registerCustomer(name, meter, type, phone, address) {
                    showAddDialog = false
                }
            }
        )
    }

    // Edit Customer Dialog
    if (customerToEdit != null) {
        CustomerDialog(
            initialCustomer = customerToEdit,
            onDismiss = { customerToEdit = null },
            onSave = { name, meter, type, phone, address ->
                val updated = customerToEdit!!.copy(
                    customerName = name,
                    meterNumber = meter,
                    customerType = type.name,
                    phone = phone,
                    address = address
                )
                viewModel.updateCustomer(updated) {
                    customerToEdit = null
                }
            }
        )
    }

    // Details & Billing History Dialog
    if (selectedCustomerForDetails != null) {
        CustomerHistoryDialog(
            customer = selectedCustomerForDetails!!,
            viewModel = viewModel,
            onDismiss = { selectedCustomerForDetails = null }
        )
    }
}

@Composable
fun CustomerCard(
    customer: Customer,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleStatus: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID: #${customer.id} • Meter: ${customer.meterNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WaterBluePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Type Badge
                val isResidence = customer.customerType == CustomerType.RESIDENCE.name
                Surface(
                    color = if (isResidence) Color(0xFFE0F2F1) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isResidence) "መኖርያ (Residence)" else "ድርጅት (Organization)",
                        color = if (isResidence) Color(0xFF00796B) else Color(0xFFE65100),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Phone: ${customer.phone.ifBlank { "N/A" }} • ${customer.address.ifBlank { "No address" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                // Active / Inactive status indicator
                Surface(
                    color = if (customer.active) WaterGreenPaid.copy(alpha = 0.15f) else WaterRedExpired.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (customer.active) "Active" else "Deactivated",
                        color = if (customer.active) WaterGreenPaid else WaterRedExpired,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClick) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("History", fontSize = 12.sp)
                }

                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }

                TextButton(
                    onClick = onToggleStatus,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (customer.active) WaterRedExpired else WaterGreenPaid
                    )
                ) {
                    Icon(
                        imageVector = if (customer.active) Icons.Default.Block else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (customer.active) "Deactivate" else "Activate", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDialog(
    initialCustomer: Customer?,
    onDismiss: () -> Unit,
    onSave: (name: String, meter: String, type: CustomerType, phone: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf(initialCustomer?.customerName ?: "") }
    var meterNumber by remember { mutableStateOf(initialCustomer?.meterNumber ?: "") }
    var selectedType by remember {
        mutableStateOf(
            if (initialCustomer?.customerType == CustomerType.ORGANIZATION.name) CustomerType.ORGANIZATION else CustomerType.RESIDENCE
        )
    }
    var phone by remember { mutableStateOf(initialCustomer?.phone ?: "") }
    var address by remember { mutableStateOf(initialCustomer?.address ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (initialCustomer == null) "Register New Customer" else "Edit Customer Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WaterBluePrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    label = { Text("Customer Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cust_dialog_name")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = meterNumber,
                    onValueChange = { meterNumber = it; validationError = null },
                    label = { Text("Unique Meter Number *") },
                    placeholder = { Text("e.g. MTR-005") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cust_dialog_meter")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Customer Type *",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == CustomerType.RESIDENCE,
                        onClick = { selectedType = CustomerType.RESIDENCE },
                        label = { Text("መኖርያ (Residence)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == CustomerType.ORGANIZATION,
                        onClick = { selectedType = CustomerType.ORGANIZATION },
                        label = { Text("ድርጅት (Organization)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+2519...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cust_dialog_phone")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Kebele / House No.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cust_dialog_address")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank() || meterNumber.isBlank()) {
                                validationError = "Name and Meter Number are required."
                                return@Button
                            }
                            onSave(name, meterNumber, selectedType, phone, address)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                        modifier = Modifier.testTag("cust_dialog_save")
                    ) {
                        Text("Save Customer")
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerHistoryDialog(
    customer: Customer,
    viewModel: WaterViewModel,
    onDismiss: () -> Unit
) {
    val readings by viewModel.repository.getReadingsForCustomer(customer.id)
        .collectAsState(initial = emptyList())
    val bills by viewModel.repository.getBillsForCustomer(customer.id)
        .collectAsState(initial = emptyList())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = customer.customerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Meter: ${customer.meterNumber} • Reg: ${customer.registrationDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Billing & Consumption History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (bills.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No bills recorded for this customer yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bills) { bill ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = bill.billMonth, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = "${"%.2f".format(bill.totalPayable)} Birr",
                                            fontWeight = FontWeight.Bold,
                                            color = WaterBluePrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Readings: ${bill.previousReading.toInt()} -> ${bill.lastReading.toInt()} (Consumed: ${bill.waterConsumed.toInt()} m³)",
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Charge: ${bill.waterCharge} Birr + Rent: ${bill.meterRent} Birr • Status: ${bill.paymentStatus}",
                                        fontSize = 11.sp,
                                        color = if (bill.paymentStatus == "PAID") WaterGreenPaid else Color(0xFFD84315)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
