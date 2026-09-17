package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.security.DeviceIdentity
import com.example.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WaterRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val userDao = database.userDao()
    private val customerDao = database.customerDao()
    private val readingDao = database.meterReadingDao()
    private val tariffDao = database.tariffDao()
    private val meterRentDao = database.meterRentDao()
    private val billDao = database.billDao()
    private val paymentDao = database.paymentDao()
    private val licenseDao = database.licenseDao()
    private val auditDao = database.auditLogDao()
    private val settingDao = database.appSettingDao()

    val installationId: String by lazy {
        DeviceIdentity.getOrCreateInstallationId(context)
    }

    // --- Users & Authentication ---
    val allUsers: Flow<List<User>> = userDao.getAllUsers()

    suspend fun login(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        val user = userDao.getUserByUsername(username.trim())
            ?: return@withContext Result.failure(Exception("Invalid username or user is deactivated"))

        if (!user.active) {
            return@withContext Result.failure(Exception("This user account has been deactivated"))
        }

        val isValid = PasswordHasher.verifyPassword(password, user.salt, user.passwordHash)
        if (!isValid) {
            return@withContext Result.failure(Exception("Wrong password"))
        }

        logAudit(user.id, user.username, "LOGIN", "User logged in successfully")
        Result.success(user)
    }

    suspend fun createUser(
        actor: User,
        username: String,
        password: String,
        role: UserRole
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (actor.role != UserRole.DEVELOPER.name) {
            return@withContext Result.failure(Exception("Only Developer can create users"))
        }
        val existing = userDao.getUserByUsername(username.trim())
        if (existing != null) {
            return@withContext Result.failure(Exception("Username already exists"))
        }
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(password, salt)
        val newUser = User(
            username = username.trim(),
            passwordHash = hash,
            salt = salt,
            role = role.name,
            active = true
        )
        val id = userDao.insertUser(newUser)
        logAudit(actor.id, actor.username, "CREATE_USER", "Created ${role.displayName}: ${username.trim()}")
        Result.success(id)
    }

    suspend fun resetPassword(
        actor: User,
        targetUserId: Long,
        newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (actor.role != UserRole.DEVELOPER.name && actor.id != targetUserId) {
            return@withContext Result.failure(Exception("Unauthorized to reset password"))
        }
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(newPassword, salt)
        userDao.resetPassword(targetUserId, hash, salt)
        val targetUser = userDao.getUserById(targetUserId)
        logAudit(actor.id, actor.username, "RESET_PASSWORD", "Reset password for user #${targetUserId} (${targetUser?.username ?: ""})")
        Result.success(Unit)
    }

    suspend fun toggleUserActive(
        actor: User,
        targetUserId: Long,
        active: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (actor.role != UserRole.DEVELOPER.name) {
            return@withContext Result.failure(Exception("Only Developer can manage user status"))
        }
        if (actor.id == targetUserId && !active) {
            return@withContext Result.failure(Exception("Developer cannot deactivate their own account"))
        }
        userDao.setUserActive(targetUserId, active)
        val targetUser = userDao.getUserById(targetUserId)
        logAudit(
            actor.id,
            actor.username,
            "TOGGLE_USER_STATUS",
            "${if (active) "Activated" else "Deactivated"} user: ${targetUser?.username ?: ""}"
        )
        Result.success(Unit)
    }

    suspend fun deleteUser(
        actor: User,
        targetUserId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (actor.role != UserRole.DEVELOPER.name) {
            return@withContext Result.failure(Exception("Only Developer can delete users"))
        }
        if (actor.id == targetUserId) {
            return@withContext Result.failure(Exception("Cannot delete the active Developer account"))
        }
        val targetUser = userDao.getUserById(targetUserId)
        userDao.deleteUser(targetUserId)
        logAudit(actor.id, actor.username, "DELETE_USER", "Deleted user ${targetUser?.username ?: ""}")
        Result.success(Unit)
    }

    // --- Customers ---
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers()
    val activeCustomers: Flow<List<Customer>> = customerDao.getActiveCustomers()
    val totalCustomersCount: Flow<Int> = customerDao.getTotalCustomersCount()
    val activeCustomersCount: Flow<Int> = customerDao.getActiveCustomersCount()

    fun searchCustomers(query: String): Flow<List<Customer>> = customerDao.searchCustomers(query)

    suspend fun registerCustomer(
        actor: User,
        name: String,
        meterNumber: String,
        type: CustomerType,
        phone: String,
        address: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val cleanMeter = meterNumber.trim().uppercase()
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withContext Result.failure(Exception("Customer name cannot be empty"))
        if (cleanMeter.isBlank()) return@withContext Result.failure(Exception("Meter number cannot be empty"))

        val existing = customerDao.getCustomerByMeterNumber(cleanMeter)
        if (existing != null) {
            return@withContext Result.failure(Exception("Duplicate meter number: $cleanMeter already assigned to ${existing.customerName}"))
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val customer = Customer(
            customerName = cleanName,
            meterNumber = cleanMeter,
            customerType = type.name,
            phone = phone.trim(),
            address = address.trim(),
            registrationDate = dateStr,
            active = true
        )
        val id = customerDao.insertCustomer(customer)
        logAudit(actor.id, actor.username, "REGISTER_CUSTOMER", "Registered customer $cleanName with Meter $cleanMeter ($type)")
        Result.success(id)
    }

    suspend fun updateCustomer(
        actor: User,
        customer: Customer
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = customerDao.getCustomerByMeterNumber(customer.meterNumber.trim().uppercase())
        if (existing != null && existing.id != customer.id) {
            return@withContext Result.failure(Exception("Duplicate meter number: ${customer.meterNumber} already in use"))
        }
        customerDao.updateCustomer(customer.copy(meterNumber = customer.meterNumber.trim().uppercase()))
        logAudit(actor.id, actor.username, "UPDATE_CUSTOMER", "Updated customer: ${customer.customerName}")
        Result.success(Unit)
    }

    suspend fun setCustomerActive(
        actor: User,
        customerId: Long,
        active: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        customerDao.setCustomerActive(customerId, active)
        val c = customerDao.getCustomerById(customerId)
        logAudit(actor.id, actor.username, "STATUS_CUSTOMER", "${if (active) "Activated" else "Deactivated"} customer: ${c?.customerName ?: ""}")
        Result.success(Unit)
    }

    suspend fun getCustomerById(id: Long): Customer? = withContext(Dispatchers.IO) {
        customerDao.getCustomerById(id)
    }

    suspend fun getCustomerByMeter(meter: String): Customer? = withContext(Dispatchers.IO) {
        customerDao.getCustomerByMeterNumber(meter.trim().uppercase())
    }

    // --- Tariffs & Meter Rents ---
    val allTariffs: Flow<List<Tariff>> = tariffDao.getAllTariffs()
    val allMeterRents: Flow<List<MeterRent>> = meterRentDao.getAllMeterRents()

    fun getTariffsForType(type: CustomerType): Flow<List<Tariff>> = tariffDao.getActiveTariffsForType(type.name)

    suspend fun addTariff(
        actor: User,
        type: CustomerType,
        minUsage: Double,
        maxUsage: Double,
        price: Double
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (minUsage > maxUsage) {
            return@withContext Result.failure(Exception("Minimum usage cannot exceed maximum usage"))
        }
        if (price < 0) {
            return@withContext Result.failure(Exception("Price cannot be negative"))
        }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val tariff = Tariff(
            customerType = type.name,
            minUsage = minUsage,
            maxUsage = maxUsage,
            price = price,
            effectiveDate = dateStr,
            active = true
        )
        val id = tariffDao.insertTariff(tariff)
        logAudit(actor.id, actor.username, "ADD_TARIFF", "Added tariff for $type: $minUsage-$maxUsage at $price Birr")
        Result.success(id)
    }

    suspend fun updateTariff(
        actor: User,
        tariff: Tariff
    ): Result<Unit> = withContext(Dispatchers.IO) {
        tariffDao.updateTariff(tariff)
        logAudit(actor.id, actor.username, "UPDATE_TARIFF", "Updated tariff #${tariff.id} for ${tariff.customerType} to ${tariff.price} Birr")
        Result.success(Unit)
    }

    suspend fun deleteTariff(
        actor: User,
        tariff: Tariff
    ): Result<Unit> = withContext(Dispatchers.IO) {
        tariffDao.deleteTariff(tariff)
        logAudit(actor.id, actor.username, "DELETE_TARIFF", "Deleted tariff tier #${tariff.id}")
        Result.success(Unit)
    }

    suspend fun updateMeterRent(
        actor: User,
        type: CustomerType,
        amount: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (amount < 0) {
            return@withContext Result.failure(Exception("Meter rent cannot be negative"))
        }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val existing = meterRentDao.getActiveMeterRent(type.name)
        if (existing != null) {
            meterRentDao.updateMeterRent(existing.copy(amount = amount, effectiveDate = dateStr))
        } else {
            meterRentDao.insertMeterRent(MeterRent(customerType = type.name, amount = amount, effectiveDate = dateStr))
        }
        logAudit(actor.id, actor.username, "UPDATE_METER_RENT", "Set ${type.displayName} meter rent to $amount Birr")
        Result.success(Unit)
    }

    suspend fun getMeterRent(type: CustomerType): Double = withContext(Dispatchers.IO) {
        meterRentDao.getActiveMeterRent(type.name)?.amount ?: if (type == CustomerType.RESIDENCE) 30.0 else 60.0
    }

    // --- Meter Readings & Billing Calculation ---
    val allReadings: Flow<List<MeterReading>> = readingDao.getAllReadings()
    val allBills: Flow<List<Bill>> = billDao.getAllBills()

    fun getReadingsForCustomer(customerId: Long): Flow<List<MeterReading>> = readingDao.getReadingsForCustomer(customerId)
    fun getBillsForCustomer(customerId: Long): Flow<List<Bill>> = billDao.getBillsForCustomer(customerId)
    fun searchBills(query: String): Flow<List<Bill>> = billDao.searchBills(query)

    suspend fun getLatestReading(customerId: Long): MeterReading? = withContext(Dispatchers.IO) {
        readingDao.getLatestReadingForCustomer(customerId)
    }

    /**
     * Preview calculation without committing to database
     */
    suspend fun previewBillCalculation(
        customerId: Long,
        lastReading: Double
    ): Result<BillCalculationPreview> = withContext(Dispatchers.IO) {
        val customer = customerDao.getCustomerById(customerId)
            ?: return@withContext Result.failure(Exception("Customer not found"))

        val latest = readingDao.getLatestReadingForCustomer(customerId)
        val previousReading = latest?.lastReading ?: 0.0

        if (lastReading < previousReading) {
            return@withContext Result.failure(Exception("Last reading cannot be lower than previous reading."))
        }

        val waterConsumed = lastReading - previousReading
        val matchingTariff = tariffDao.findMatchingTariff(customer.customerType, waterConsumed)
            ?: return@withContext Result.failure(
                Exception("No active tariff configured for ${customer.customerType} with consumption of $waterConsumed m³")
            )

        val waterCharge = waterConsumed * matchingTariff.price
        val meterRent = getMeterRent(CustomerType.valueOf(customer.customerType))
        val totalPayable = waterCharge + meterRent

        Result.success(
            BillCalculationPreview(
                previousReading = previousReading,
                lastReading = lastReading,
                waterConsumed = waterConsumed,
                unitPrice = matchingTariff.price,
                waterCharge = waterCharge,
                meterRent = meterRent,
                totalPayable = totalPayable,
                tariffId = matchingTariff.id
            )
        )
    }

    /**
     * Records the meter reading and creates an immutable historical bill.
     */
    suspend fun saveReadingAndGenerateBill(
        actor: User,
        customerId: Long,
        lastReading: Double,
        billMonth: String
    ): Result<Bill> = withContext(Dispatchers.IO) {
        val customer = customerDao.getCustomerById(customerId)
            ?: return@withContext Result.failure(Exception("Customer not found"))

        if (!customer.active) {
            return@withContext Result.failure(Exception("Cannot record reading for deactivated customer"))
        }

        // Duplicate bill check for the customer in the same month
        val existingBill = billDao.getBillByCustomerAndMonth(customerId, billMonth.trim())
        if (existingBill != null) {
            return@withContext Result.failure(Exception("A bill already exists for this customer for this month."))
        }

        val latest = readingDao.getLatestReadingForCustomer(customerId)
        val previousReading = latest?.lastReading ?: 0.0

        if (lastReading < previousReading) {
            return@withContext Result.failure(Exception("Last reading cannot be lower than previous reading."))
        }

        val waterConsumed = lastReading - previousReading
        val matchingTariff = tariffDao.findMatchingTariff(customer.customerType, waterConsumed)
            ?: return@withContext Result.failure(
                Exception("No active tariff configured for ${customer.customerType} at $waterConsumed m³")
            )

        val waterCharge = waterConsumed * matchingTariff.price
        val meterRent = getMeterRent(CustomerType.valueOf(customer.customerType))
        val totalPayable = waterCharge + meterRent
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Insert reading
        val reading = MeterReading(
            customerId = customerId,
            meterNumber = customer.meterNumber,
            readingDate = currentDate,
            previousReading = previousReading,
            lastReading = lastReading,
            waterConsumed = waterConsumed,
            readingUserId = actor.id,
            readingUserName = actor.username,
            billMonth = billMonth.trim()
        )
        readingDao.insertReading(reading)

        // Insert historical bill (preserves tariff and calculated values forever)
        val bill = Bill(
            customerId = customerId,
            customerName = customer.customerName,
            meterNumber = customer.meterNumber,
            customerType = customer.customerType,
            billMonth = billMonth.trim(),
            readingDate = currentDate,
            previousReading = previousReading,
            lastReading = lastReading,
            waterConsumed = waterConsumed,
            waterCharge = waterCharge,
            meterRent = meterRent,
            totalPayable = totalPayable,
            tariffId = matchingTariff.id,
            tariffPriceApplied = matchingTariff.price,
            amountPaid = 0.0,
            paymentStatus = "UNPAID"
        )
        val billId = billDao.insertBill(bill)
        val createdBill = bill.copy(id = billId)

        logAudit(
            actor.id,
            actor.username,
            "RECORD_READING_AND_BILL",
            "Recorded reading for ${customer.customerName} (Meter ${customer.meterNumber}): Consumed $waterConsumed m³, Total: $totalPayable Birr ($billMonth)"
        )
        Result.success(createdBill)
    }

    // --- Payments & Receipts ---
    val allPayments: Flow<List<Payment>> = paymentDao.getAllPayments()

    fun getPaymentsForBill(billId: Long): Flow<List<Payment>> = paymentDao.getPaymentsForBill(billId)

    suspend fun recordPayment(
        actor: User,
        billId: Long,
        amountToPay: Double
    ): Result<Payment> = withContext(Dispatchers.IO) {
        if (amountToPay <= 0) {
            return@withContext Result.failure(Exception("Payment amount must be greater than zero"))
        }

        val bill = billDao.getBillById(billId)
            ?: return@withContext Result.failure(Exception("Bill not found"))

        val remainingBefore = bill.totalPayable - bill.amountPaid
        if (amountToPay > remainingBefore + 0.01) {
            return@withContext Result.failure(Exception("Payment amount (${amountToPay} Birr) exceeds remaining balance (${remainingBefore} Birr)"))
        }

        val newTotalPaid = (bill.amountPaid + amountToPay).coerceAtMost(bill.totalPayable)
        val newRemaining = (bill.totalPayable - newTotalPaid).coerceAtLeast(0.0)
        val newStatus = when {
            newRemaining <= 0.01 -> "PAID"
            newTotalPaid > 0 -> "PARTIALLY PAID"
            else -> "UNPAID"
        }

        billDao.updatePaymentStatus(billId, newTotalPaid, newStatus)

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val count = paymentDao.getPaymentsCount() + 1
        val receiptNumber = "RCP-${SimpleDateFormat("yyyyMM", Locale.getDefault()).format(Date())}-${"%04d".format(count)}"

        val payment = Payment(
            billId = billId,
            customerName = bill.customerName,
            meterNumber = bill.meterNumber,
            billMonth = bill.billMonth,
            amountPaid = amountToPay,
            totalPayable = bill.totalPayable,
            remainingBalance = newRemaining,
            paymentDate = dateStr,
            accountantId = actor.id,
            accountantName = actor.username,
            receiptNumber = receiptNumber
        )
        val paymentId = paymentDao.insertPayment(payment)

        logAudit(
            actor.id,
            actor.username,
            "RECORD_PAYMENT",
            "Collected $amountToPay Birr for ${bill.customerName} (${bill.billMonth}). Receipt: $receiptNumber"
        )
        Result.success(payment.copy(id = paymentId))
    }

    // --- Dashboard & Reports Data ---
    fun getBillsCountThisMonth(month: String): Flow<Int> = billDao.getBillsCountThisMonth(month)
    fun getPaidBillsCount(): Flow<Int> = billDao.getPaidBillsCount()
    fun getUnpaidBillsCount(): Flow<Int> = billDao.getUnpaidBillsCount()
    fun getTotalBilledThisMonth(month: String): Flow<Double> = billDao.getTotalBilledThisMonth(month)
    fun getTotalCollectedThisMonth(month: String): Flow<Double> = billDao.getTotalCollectedThisMonth(month)
    fun getDailyCollection(date: String): Flow<Double> = paymentDao.getDailyCollection(date)
    fun getTodayReadingsCount(date: String): Flow<Int> = readingDao.getCountForDate(date)

    // --- License Management ---
    val latestLicense: Flow<License?> = licenseDao.getLatestLicense()

    suspend fun getLicenseStatus(): LicenseStatusInfo = withContext(Dispatchers.IO) {
        val lic = licenseDao.getLatestLicenseSync()
        if (lic == null) {
            return@withContext LicenseStatusInfo(
                license = null,
                status = "EXPIRED",
                remainingDays = 0,
                isUsable = false
            )
        }
        val now = System.currentTimeMillis()
        val remainingMs = lic.expiryDate - now
        val remainingDays = (remainingMs / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)

        val calculatedStatus = when {
            remainingMs <= 0 -> "EXPIRED"
            remainingDays <= 7 -> "EXPIRING_SOON"
            else -> "ACTIVE"
        }

        if (calculatedStatus != lic.status) {
            licenseDao.updateLicense(lic.copy(status = calculatedStatus))
        }

        LicenseStatusInfo(
            license = lic,
            status = calculatedStatus,
            remainingDays = remainingDays,
            isUsable = calculatedStatus != "EXPIRED"
        )
    }

    suspend fun activateLicense(
        actor: User?,
        licenseType: String // "1 Month", "3 Months", "6 Months", "9 Months", "1 Year"
    ): Result<License> = withContext(Dispatchers.IO) {
        val monthsToAdd = when (licenseType) {
            "1 Month" -> 1
            "3 Months" -> 3
            "6 Months" -> 6
            "9 Months" -> 9
            "1 Year" -> 12
            else -> 1
        }
        val cal = Calendar.getInstance()
        val startTime = cal.timeInMillis
        cal.add(Calendar.MONTH, monthsToAdd)
        val expiryTime = cal.timeInMillis

        val license = License(
            licenseType = licenseType,
            startDate = startTime,
            expiryDate = expiryTime,
            status = "ACTIVE",
            installationId = installationId
        )
        val id = licenseDao.insertLicense(license)
        val saved = license.copy(id = id)

        actor?.let {
            logAudit(it.id, it.username, "ACTIVATE_LICENSE", "Activated $licenseType license for device $installationId until ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(expiryTime))}")
        }
        Result.success(saved)
    }

    // --- App Settings ---
    fun getAppNameFlow(): Flow<String?> = settingDao.getSettingFlow("app_name")

    suspend fun getAppName(): String = withContext(Dispatchers.IO) {
        settingDao.getSetting("app_name")?.value ?: "WATER MANAGEMENT SYSTEM"
    }

    suspend fun setAppName(actor: User, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (clean.isBlank()) return@withContext Result.failure(Exception("Application name cannot be empty"))
        settingDao.setSetting(AppSetting("app_name", clean))
        logAudit(actor.id, actor.username, "CHANGE_APP_NAME", "Updated application name to: $clean")
        Result.success(Unit)
    }

    suspend fun isInitialSetupCompleted(): Boolean = withContext(Dispatchers.IO) {
        settingDao.getSetting("initial_setup_completed")?.value == "true"
    }

    suspend fun setInitialSetupCompleted(): Unit = withContext(Dispatchers.IO) {
        settingDao.setSetting(AppSetting("initial_setup_completed", "true"))
    }

    // --- Audit Logs ---
    val recentAuditLogs: Flow<List<AuditLog>> = auditDao.getRecentLogs(200)

    suspend fun logAudit(userId: Long, username: String, action: String, description: String) {
        auditDao.insertLog(
            AuditLog(
                userId = userId,
                username = username,
                action = action,
                description = description,
                dateTime = System.currentTimeMillis()
            )
        )
    }

    // --- Developer Actions: Reset Data, Backup, Restore ---
    suspend fun resetAllApplicationData(actor: User): Result<Unit> = withContext(Dispatchers.IO) {
        if (actor.role != UserRole.DEVELOPER.name) {
            return@withContext Result.failure(Exception("Only Developer can reset application data"))
        }
        database.clearAllTables()
        AppDatabase.populateInitialData(database)
        setInitialSetupCompleted()
        logAudit(actor.id, actor.username, "RESET_DATA", "Reset all database tables and restored initial state")
        Result.success(Unit)
    }

    suspend fun exportDatabaseBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app_name", getAppName())
        root.put("installation_id", installationId)
        root.put("export_timestamp", System.currentTimeMillis())

        val customersList = customerDao.getAllCustomers().firstOrNull() ?: emptyList()
        val customersJson = JSONArray()
        customersList.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("customerName", c.customerName)
            obj.put("meterNumber", c.meterNumber)
            obj.put("customerType", c.customerType)
            obj.put("phone", c.phone)
            obj.put("address", c.address)
            obj.put("registrationDate", c.registrationDate)
            obj.put("active", c.active)
            customersJson.put(obj)
        }
        root.put("customers", customersJson)

        val billsList = billDao.getAllBills().firstOrNull() ?: emptyList()
        val billsJson = JSONArray()
        billsList.forEach { b ->
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("customerId", b.customerId)
            obj.put("customerName", b.customerName)
            obj.put("meterNumber", b.meterNumber)
            obj.put("customerType", b.customerType)
            obj.put("billMonth", b.billMonth)
            obj.put("readingDate", b.readingDate)
            obj.put("previousReading", b.previousReading)
            obj.put("lastReading", b.lastReading)
            obj.put("waterConsumed", b.waterConsumed)
            obj.put("waterCharge", b.waterCharge)
            obj.put("meterRent", b.meterRent)
            obj.put("totalPayable", b.totalPayable)
            obj.put("amountPaid", b.amountPaid)
            obj.put("paymentStatus", b.paymentStatus)
            billsJson.put(obj)
        }
        root.put("bills", billsJson)

        root.toString(2)
    }

    suspend fun restoreDatabaseFromJson(actor: User, jsonString: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("customers") || !root.has("bills")) {
                return@withContext Result.failure(Exception("Invalid backup file structure"))
            }

            val customersJson = root.getJSONArray("customers")
            for (i in 0 until customersJson.length()) {
                val obj = customersJson.getJSONObject(i)
                val meter = obj.getString("meterNumber")
                val existing = customerDao.getCustomerByMeterNumber(meter)
                if (existing == null) {
                    customerDao.insertCustomer(
                        Customer(
                            customerName = obj.getString("customerName"),
                            meterNumber = meter,
                            customerType = obj.getString("customerType"),
                            phone = obj.optString("phone", ""),
                            address = obj.optString("address", ""),
                            registrationDate = obj.optString("registrationDate", ""),
                            active = obj.optBoolean("active", true)
                        )
                    )
                }
            }

            logAudit(actor.id, actor.username, "RESTORE_BACKUP", "Restored data from local backup file")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to restore backup: ${e.message}"))
        }
    }
}

data class BillCalculationPreview(
    val previousReading: Double,
    val lastReading: Double,
    val waterConsumed: Double,
    val unitPrice: Double,
    val waterCharge: Double,
    val meterRent: Double,
    val totalPayable: Double,
    val tariffId: Long
)

data class LicenseStatusInfo(
    val license: License?,
    val status: String, // "ACTIVE", "EXPIRING_SOON", "EXPIRED"
    val remainingDays: Int,
    val isUsable: Boolean
)
