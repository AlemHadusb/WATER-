package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User roles
 */
enum class UserRole(val displayName: String) {
    DEVELOPER("Developer"),
    ADMIN("Admin"),
    ACCOUNTANT("Accountant"),
    READING_USER("Reading User")
}

/**
 * Customer types supporting Tigrinya and English
 */
enum class CustomerType(val amharicName: String, val englishName: String) {
    RESIDENCE("መኖርያ", "Residence"),
    ORGANIZATION("ድርጅት", "Organization");

    val displayName: String get() = "$amharicName ($englishName)"

    override fun toString(): String = displayName
}

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val role: String, // from UserRole.name
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "customers",
    indices = [Index(value = ["meterNumber"], unique = true)]
)
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val meterNumber: String,
    val customerType: String, // CustomerType.RESIDENCE.name or ORGANIZATION.name
    val phone: String,
    val address: String,
    val registrationDate: String,
    val active: Boolean = true
)

@Entity(tableName = "meter_readings")
data class MeterReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val meterNumber: String,
    val readingDate: String,
    val previousReading: Double,
    val lastReading: Double,
    val waterConsumed: Double,
    val readingUserId: Long,
    val readingUserName: String,
    val billMonth: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tariffs")
data class Tariff(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerType: String, // RESIDENCE or ORGANIZATION
    val minUsage: Double,
    val maxUsage: Double,
    val price: Double,
    val effectiveDate: String,
    val active: Boolean = true
)

@Entity(tableName = "meter_rent")
data class MeterRent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerType: String, // RESIDENCE or ORGANIZATION
    val amount: Double,
    val effectiveDate: String,
    val active: Boolean = true
)

@Entity(
    tableName = "bills",
    indices = [Index(value = ["customerId", "billMonth"], unique = true)]
)
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val customerName: String,
    val meterNumber: String,
    val customerType: String,
    val billMonth: String,
    val readingDate: String,
    val previousReading: Double,
    val lastReading: Double,
    val waterConsumed: Double,
    val waterCharge: Double,
    val meterRent: Double,
    val totalPayable: Double,
    val tariffId: Long? = null,
    val tariffPriceApplied: Double = 0.0,
    val amountPaid: Double = 0.0,
    val paymentStatus: String = "UNPAID", // UNPAID, PARTIALLY PAID, PAID
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val customerName: String,
    val meterNumber: String,
    val billMonth: String,
    val amountPaid: Double,
    val totalPayable: Double,
    val remainingBalance: Double,
    val paymentDate: String,
    val accountantId: Long,
    val accountantName: String,
    val receiptNumber: String
)

@Entity(tableName = "licenses")
data class License(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val licenseType: String, // "1 Month", "3 Months", "6 Months", "9 Months", "1 Year"
    val startDate: Long,
    val expiryDate: Long,
    val status: String, // "ACTIVE", "EXPIRING_SOON", "EXPIRED"
    val installationId: String
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val username: String,
    val action: String,
    val description: String,
    val dateTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
