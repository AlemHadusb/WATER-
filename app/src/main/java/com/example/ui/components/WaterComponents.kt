package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Payment
import com.example.data.model.UserRole
import com.example.data.repository.LicenseStatusInfo
import com.example.ui.theme.*

@Composable
fun WaterLogoIcon(modifier: Modifier = Modifier, size: Int = 48) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(WaterBluePrimary, WaterCyanSecondary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.WaterDrop,
            contentDescription = "Water Management Logo",
            tint = Color.White,
            modifier = Modifier.size((size * 0.6).dp)
        )
    }
}

@Composable
fun RoleBadge(role: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (role) {
        UserRole.DEVELOPER.name -> Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), "Developer")
        UserRole.ADMIN.name -> Triple(Color(0xFFE1F5FE), Color(0xFF0277BD), "Admin")
        UserRole.ACCOUNTANT.name -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Accountant")
        UserRole.READING_USER.name -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Reading User")
        else -> Triple(Color(0xFFECEFF1), Color(0xFF455A64), role)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterTopAppBar(
    title: String,
    appName: String,
    role: String?,
    licenseInfo: LicenseStatusInfo?,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // License status alert banner if expired or expiring soon
            if (licenseInfo != null && licenseInfo.status != "ACTIVE") {
                val isExpired = licenseInfo.status == "EXPIRED"
                Surface(
                    color = if (isExpired) WaterRedExpired else WaterOrangePending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isExpired) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isExpired)
                                "SYSTEM LICENSE EXPIRED — Developer renewal required!"
                            else
                                "License expiring in ${licenseInfo.remainingDays} days",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WaterLogoIcon(size = 36)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (role != null) {
                        RoleBadge(role = role)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onLogoutClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

@Composable
fun StatsCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiptDialog(
    payment: Payment,
    appName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                WaterLogoIcon(size = 44)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = appName,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = WaterBluePrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "OFFICIAL WATER BILL RECEIPT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Receipt #: ${payment.receiptNumber}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Customer & Bill Details Table
                ReceiptRow(label = "Customer Name:", value = payment.customerName)
                ReceiptRow(label = "Meter Number:", value = payment.meterNumber)
                ReceiptRow(label = "Billing Month:", value = payment.billMonth)
                ReceiptRow(label = "Payment Date:", value = payment.paymentDate)
                ReceiptRow(label = "Accountant:", value = payment.accountantName)

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                ReceiptRow(label = "Total Bill Payable:", value = "${"%.2f".format(payment.totalPayable)} Birr")
                ReceiptRow(label = "Remaining Balance:", value = "${"%.2f".format(payment.remainingBalance)} Birr")

                Spacer(modifier = Modifier.height(12.dp))

                // Highlighted TOTAL PAID box
                Surface(
                    color = WaterBlueLight,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, WaterBluePrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TOTAL PAID",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlueDark
                        )
                        Text(
                            text = "${"%.2f".format(payment.amountPaid)} Birr",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = WaterGreenPaid
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Signature Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(1.dp)
                                .background(Color.Gray)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Customer Signature", fontSize = 10.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(1.dp)
                                .background(Color.Gray)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Accountant Stamp/Sign", fontSize = 10.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Share / Print and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }

                    Button(
                        onClick = {
                            shareReceiptText(context, payment, appName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print / Share")
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun shareReceiptText(context: Context, payment: Payment, appName: String) {
    val text = """
        ==============================
        $appName
        OFFICIAL WATER BILL RECEIPT
        Receipt #: ${payment.receiptNumber}
        ==============================
        Customer: ${payment.customerName}
        Meter Number: ${payment.meterNumber}
        Bill Month: ${payment.billMonth}
        Payment Date: ${payment.paymentDate}
        Accountant: ${payment.accountantName}
        ------------------------------
        Total Bill: ${"%.2f".format(payment.totalPayable)} Birr
        AMOUNT PAID: ${"%.2f".format(payment.amountPaid)} Birr
        Remaining: ${"%.2f".format(payment.remainingBalance)} Birr
        ==============================
        Thank you for your payment!
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Receipt ${payment.receiptNumber}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Print / Share Receipt"))
}
