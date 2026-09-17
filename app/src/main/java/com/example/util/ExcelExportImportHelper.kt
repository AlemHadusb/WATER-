package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.Bill
import com.example.data.model.Customer
import com.example.data.model.MeterReading
import com.example.data.model.Payment
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust CSV/Excel Export & Import helper for Water Management System.
 * Supports exporting Customers, Readings, Bills, Payments, and Financial Summary
 * in standard CSV format with UTF-8 BOM so Microsoft Excel, LibreOffice Calc,
 * and Google Sheets open them cleanly without character encoding issues.
 */
object ExcelExportImportHelper {

    // UTF-8 Byte Order Mark so Excel opens UTF-8 text (including Amharic/Tigrinya) correctly
    private const val UTF8_BOM = "\uFEFF"

    /**
     * Escapes fields according to standard CSV (RFC 4180):
     * Quotes fields containing commas, quotes, or newlines, and doubles quotes inside.
     */
    fun escapeCsv(value: Any?): String {
        if (value == null) return ""
        val stringValue = value.toString()
        return if (stringValue.contains(",") || stringValue.contains("\"") || stringValue.contains("\n") || stringValue.contains("\r")) {
            "\"" + stringValue.replace("\"", "\"\"") + "\""
        } else {
            stringValue
        }
    }

    /**
     * Parses a single CSV line into tokens, handling quotes and escaped quotes properly.
     */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '\"') {
                    if (i + 1 < line.length && line[i + 1] == '\"') {
                        // Escaped quote
                        sb.append('\"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(c)
                }
            } else {
                if (c == '\"') {
                    inQuotes = true
                } else if (c == ',') {
                    result.add(sb.toString().trim())
                    sb.clear()
                } else {
                    sb.append(c)
                }
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    // --- GENERATE CUSTOMERS CSV ---
    fun generateCustomersCsv(customers: List<Customer>): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        // Headers
        sb.append("Customer ID,Customer Name,Meter Number,Customer Type,Phone,Address,Registration Date,Status\n")
        for (c in customers) {
            val status = if (c.active) "Active" else "Deactivated"
            sb.append(listOf(
                escapeCsv(c.id),
                escapeCsv(c.customerName),
                escapeCsv(c.meterNumber),
                escapeCsv(c.customerType),
                escapeCsv(c.phone),
                escapeCsv(c.address),
                escapeCsv(c.registrationDate),
                escapeCsv(status)
            ).joinToString(","))
            sb.append("\n")
        }
        return sb.toString()
    }

    // --- GENERATE METER READINGS CSV ---
    fun generateReadingsCsv(readings: List<MeterReading>): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.append("Reading ID,Customer ID,Meter Number,Reading Date,Previous Reading (m3),Current Reading (m3),Water Consumed (m3),Bill Month,Reading User\n")
        for (r in readings) {
            sb.append(listOf(
                escapeCsv(r.id),
                escapeCsv(r.customerId),
                escapeCsv(r.meterNumber),
                escapeCsv(r.readingDate),
                escapeCsv(r.previousReading),
                escapeCsv(r.lastReading),
                escapeCsv(r.waterConsumed),
                escapeCsv(r.billMonth),
                escapeCsv(r.readingUserName)
            ).joinToString(","))
            sb.append("\n")
        }
        return sb.toString()
    }

    // --- GENERATE BILLS CSV ---
    fun generateBillsCsv(bills: List<Bill>): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.append("Bill ID,Customer ID,Customer Name,Meter Number,Customer Type,Bill Month,Reading Date,Water Consumed (m3),Water Charge (ETB),Meter Rent (ETB),Total Payable (ETB),Amount Paid (ETB),Balance Due (ETB),Payment Status\n")
        for (b in bills) {
            val balanceDue = (b.totalPayable - b.amountPaid).coerceAtLeast(0.0)
            sb.append(listOf(
                escapeCsv(b.id),
                escapeCsv(b.customerId),
                escapeCsv(b.customerName),
                escapeCsv(b.meterNumber),
                escapeCsv(b.customerType),
                escapeCsv(b.billMonth),
                escapeCsv(b.readingDate),
                escapeCsv(b.waterConsumed),
                escapeCsv(b.waterCharge),
                escapeCsv(b.meterRent),
                escapeCsv(b.totalPayable),
                escapeCsv(b.amountPaid),
                escapeCsv(balanceDue),
                escapeCsv(b.paymentStatus)
            ).joinToString(","))
            sb.append("\n")
        }
        return sb.toString()
    }

    // --- GENERATE PAYMENTS CSV ---
    fun generatePaymentsCsv(payments: List<Payment>): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.append("Payment ID,Receipt Number,Bill ID,Customer Name,Meter Number,Bill Month,Amount Paid (ETB),Total Payable (ETB),Remaining Balance (ETB),Payment Date,Accountant\n")
        for (p in payments) {
            sb.append(listOf(
                escapeCsv(p.id),
                escapeCsv(p.receiptNumber),
                escapeCsv(p.billId),
                escapeCsv(p.customerName),
                escapeCsv(p.meterNumber),
                escapeCsv(p.billMonth),
                escapeCsv(p.amountPaid),
                escapeCsv(p.totalPayable),
                escapeCsv(p.remainingBalance),
                escapeCsv(p.paymentDate),
                escapeCsv(p.accountantName)
            ).joinToString(","))
            sb.append("\n")
        }
        return sb.toString()
    }

    // --- GENERATE FULL FINANCIAL & PERFORMANCE REPORT CSV ---
    fun generateFinancialReportCsv(
        appName: String,
        month: String,
        todayDate: String,
        totalCustomers: Int,
        activeCustomers: Int,
        todayReadings: Int,
        todayCollection: Double,
        monthlyBilled: Double,
        monthlyCollected: Double,
        outstanding: Double,
        paidCount: Int,
        unpaidCount: Int,
        bills: List<Bill>
    ): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.append("WATER MANAGEMENT SYSTEM - FINANCIAL & REVENUE REPORT\n")
        sb.append("Organization,$appName\n")
        sb.append("Report Date,$todayDate\n")
        sb.append("Report Month,$month\n\n")

        sb.append("SUMMARY METRIC,VALUE\n")
        sb.append("Total Registered Customers,$totalCustomers\n")
        sb.append("Active Customers,$activeCustomers\n")
        sb.append("Readings Recorded Today,$todayReadings\n")
        sb.append("Revenue Collected Today (ETB),${"%.2f".format(todayCollection)}\n")
        sb.append("Total Billed This Month (ETB),${"%.2f".format(monthlyBilled)}\n")
        sb.append("Total Collected This Month (ETB),${"%.2f".format(monthlyCollected)}\n")
        sb.append("Outstanding Receivable (ETB),${"%.2f".format(outstanding)}\n")
        sb.append("Paid Bills Count,$paidCount\n")
        sb.append("Unpaid / Pending Bills Count,$unpaidCount\n\n")

        sb.append("DETAILED BILLS BREAKDOWN ($month)\n")
        sb.append("Bill ID,Customer Name,Meter #,Type,Consumed (m3),Water Charge,Meter Rent,Total Payable,Amount Paid,Status\n")
        for (b in bills) {
            sb.append(listOf(
                escapeCsv(b.id),
                escapeCsv(b.customerName),
                escapeCsv(b.meterNumber),
                escapeCsv(b.customerType),
                escapeCsv(b.waterConsumed),
                escapeCsv(b.waterCharge),
                escapeCsv(b.meterRent),
                escapeCsv(b.totalPayable),
                escapeCsv(b.amountPaid),
                escapeCsv(b.paymentStatus)
            ).joinToString(","))
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Saves CSV text to cache file and generates a share Intent with Excel MIME types.
     */
    fun exportAndShareFile(
        context: Context,
        csvContent: String,
        filePrefix: String
    ): Result<File> {
        return try {
            val exportDir = File(context.cacheDir, "excel_exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
            val fileName = "${filePrefix}_${timestamp}.csv"
            val file = File(exportDir, fileName)

            file.writeText(csvContent, Charsets.UTF_8)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$filePrefix Export ($timestamp)")
                putExtra(Intent.EXTRA_TEXT, "Exported $filePrefix data from Water Management System.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Open or Share Excel/CSV File")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- IMPORT PARSING ---
    data class CustomerImportRow(
        val name: String,
        val meterNumber: String,
        val type: String, // RESIDENCE or ORGANIZATION
        val phone: String,
        val address: String
    )

    data class ImportResult(
        val totalRows: Int,
        val successfulCount: Int,
        val skippedCount: Int,
        val errors: List<String>
    )

    /**
     * Reads a CSV InputStream (from ContentResolver or file) and extracts customer records.
     */
    fun parseCustomersCsv(inputStream: InputStream): List<CustomerImportRow> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val rows = mutableListOf<CustomerImportRow>()

        var lineIndex = 0
        var headerLine: String? = null
        var nameCol = -1
        var meterCol = -1
        var typeCol = -1
        var phoneCol = -1
        var addressCol = -1

        reader.forEachLine { rawLine ->
            var line = rawLine
            if (lineIndex == 0 && line.startsWith(UTF8_BOM)) {
                line = line.removePrefix(UTF8_BOM)
            }

            val tokens = parseCsvLine(line)
            if (lineIndex == 0) {
                // Header detection
                headerLine = line
                for (col in tokens.indices) {
                    val h = tokens[col].trim().lowercase()
                    if (h.contains("name") || h.contains("customer name")) nameCol = col
                    else if (h.contains("meter") || h.contains("meter #") || h.contains("meter number")) meterCol = col
                    else if (h.contains("type") || h.contains("customer type")) typeCol = col
                    else if (h.contains("phone") || h.contains("mobile") || h.contains("contact")) phoneCol = col
                    else if (h.contains("address") || h.contains("location") || h.contains("kebele")) addressCol = col
                }

                // If headers didn't match standard names, fallback to standard column order:
                // Col 0: ID (optional) / Name, Col 1: Name / Meter, etc.
                if (nameCol == -1 && meterCol == -1) {
                    if (tokens.size >= 2) {
                        nameCol = 0
                        meterCol = 1
                        if (tokens.size > 2) typeCol = 2
                        if (tokens.size > 3) phoneCol = 3
                        if (tokens.size > 4) addressCol = 4
                    }
                }
            } else {
                if (tokens.isNotEmpty() && tokens.any { it.isNotBlank() }) {
                    val name = tokens.getOrNull(if (nameCol >= 0) nameCol else 0)?.trim() ?: ""
                    val meter = tokens.getOrNull(if (meterCol >= 0) meterCol else 1)?.trim() ?: ""
                    val type = tokens.getOrNull(if (typeCol >= 0) typeCol else 2)?.trim() ?: "RESIDENCE"
                    val phone = tokens.getOrNull(if (phoneCol >= 0) phoneCol else 3)?.trim() ?: ""
                    val address = tokens.getOrNull(if (addressCol >= 0) addressCol else 4)?.trim() ?: ""

                    if (name.isNotBlank() && meter.isNotBlank()) {
                        rows.add(
                            CustomerImportRow(
                                name = name,
                                meterNumber = meter.uppercase(),
                                type = if (type.contains("org", ignoreCase = true) || type.contains("ድርጅት")) "ORGANIZATION" else "RESIDENCE",
                                phone = phone,
                                address = address
                            )
                        )
                    }
                }
            }
            lineIndex++
        }

        return rows
    }

    /**
     * Generates a starter template CSV with sample rows that users can download/open in Excel
     * to easily prepare customer data for bulk import.
     */
    fun generateCustomerImportTemplate(): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.append("Customer Name,Meter Number,Customer Type,Phone,Address\n")
        sb.append("Abebe Kebede,WM-1001,RESIDENCE,0911234567,Kebele 04 House 120\n")
        sb.append("Mulugeta Tesfay,WM-1002,RESIDENCE,0922334455,Kebele 02 House 45\n")
        sb.append("Sunshine Bakery,WM-1003,ORGANIZATION,0933445566,Main Street Commercial Area\n")
        sb.append("Selam Health Clinic,WM-1004,ORGANIZATION,0944556677,Kebele 01 Near Hospital\n")
        return sb.toString()
    }
}
