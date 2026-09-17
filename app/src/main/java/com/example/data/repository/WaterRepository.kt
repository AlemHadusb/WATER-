package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.security.BackupCrypto
import com.example.security.DeviceIdentity
import com.example.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

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
    private val pairedDeviceDao = database.pairedDeviceDao()
    private val syncAuditLogDao = database.syncAuditLogDao()
    private val syncConflictDao = database.syncConflictDao()
    private val backupRecordDao = database.backupRecordDao()

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
            active = true,
            updatedAt = System.currentTimeMillis(),
            deviceId = installationId,
            syncStatus = "PENDING_SYNC"
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
        customerDao.updateCustomer(
            customer.copy(
                meterNumber = customer.meterNumber.trim().uppercase(),
                updatedAt = System.currentTimeMillis(),
                deviceId = installationId,
                syncStatus = "PENDING_SYNC"
            )
        )
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
            billMonth = billMonth.trim(),
            updatedAt = System.currentTimeMillis(),
            deviceId = installationId,
            syncStatus = "PENDING_SYNC"
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
            paymentStatus = "UNPAID",
            updatedAt = System.currentTimeMillis(),
            deviceId = installationId,
            syncStatus = "PENDING_SYNC"
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
            receiptNumber = receiptNumber,
            updatedAt = System.currentTimeMillis(),
            deviceId = installationId,
            syncStatus = "PENDING_SYNC"
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

    // ==========================================
    // 34-38. DEVICE-TO-DEVICE SYNC & PAIRING
    // ==========================================

    val allPairedDevices: Flow<List<PairedDevice>> = pairedDeviceDao.getActivePairedDevices()
    val pendingConflicts: Flow<List<SyncConflict>> = syncConflictDao.getPendingConflicts()
    val pendingConflictCount: Flow<Int> = syncConflictDao.getPendingConflictCount()
    val syncAuditLogs: Flow<List<SyncAuditLog>> = syncAuditLogDao.getRecentSyncLogs(100)
    val latestSyncLog: Flow<SyncAuditLog?> = syncAuditLogDao.getLatestSyncLog()
    val allBackupRecords: Flow<List<BackupRecord>> = backupRecordDao.getAllBackups()

    val pendingSyncCount: Flow<Int> = combine(
        customerDao.getPendingCustomersCount(),
        readingDao.getPendingReadingsCount(),
        billDao.getPendingBillsCount(),
        paymentDao.getPendingPaymentsCount()
    ) { c, r, b, p -> c + r + b + p }

    /**
     * Generates a temporary 6-digit pairing code that expires after 10 minutes.
     */
    suspend fun generatePairingCode(
        actor: User,
        pairedRole: String,
        pairedUsername: String,
        deviceName: String
    ): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val randomCode = (1..6).map { codeChars.random() }.joinToString("")
        val fullCode = "WMS-$randomCode"
        val expiryTime = System.currentTimeMillis() + (10 * 60 * 1000L) // 10 minutes

        val pairedDevice = PairedDevice(
            deviceId = "DEV-${UUID.randomUUID().toString().take(8).uppercase()}",
            deviceName = deviceName.ifBlank { "Remote $pairedRole Device" },
            pairedUserRole = pairedRole,
            pairedUsername = pairedUsername.ifBlank { actor.username },
            pairingCode = fullCode,
            pairingCodeExpiry = expiryTime,
            status = "PENDING_APPROVAL",
            authorizedBy = actor.username
        )
        pairedDeviceDao.insertDevice(pairedDevice)
        logAudit(actor.id, actor.username, "GENERATE_PAIRING_CODE", "Generated pairing code $fullCode for $pairedRole (Device: ${pairedDevice.deviceName})")
        Result.success(Pair(fullCode, expiryTime))
    }

    /**
     * Remote device enters or scans the pairing code to request connection.
     */
    suspend fun pairWithCode(
        actor: User,
        pairingCode: String,
        remoteDeviceName: String
    ): Result<PairedDevice> = withContext(Dispatchers.IO) {
        val cleanCode = pairingCode.trim().uppercase()
        val device = pairedDeviceDao.getDeviceByValidPairingCode(cleanCode)
            ?: return@withContext Result.failure(Exception("Invalid or expired pairing code. Please generate a new code."))

        val updated = device.copy(
            deviceName = remoteDeviceName.ifBlank { device.deviceName },
            status = "CONNECTED",
            lastSyncTimestamp = System.currentTimeMillis()
        )
        pairedDeviceDao.updateDevice(updated)
        logAudit(actor.id, actor.username, "PAIR_DEVICE", "Paired successfully with ${updated.deviceName} (${updated.pairedUserRole}) using code $cleanCode")
        Result.success(updated)
    }

    suspend fun approvePairedDevice(actor: User, deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val device = pairedDeviceDao.getDeviceById(deviceId)
            ?: return@withContext Result.failure(Exception("Device not found"))
        pairedDeviceDao.updateDeviceStatus(deviceId, "CONNECTED")
        logAudit(actor.id, actor.username, "APPROVE_DEVICE", "Approved device connection: ${device.deviceName} ($deviceId)")
        Result.success(Unit)
    }

    suspend fun disablePairedDevice(actor: User, deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val device = pairedDeviceDao.getDeviceById(deviceId)
            ?: return@withContext Result.failure(Exception("Device not found"))
        pairedDeviceDao.updateDeviceStatus(deviceId, "DISABLED")
        logAudit(actor.id, actor.username, "DISABLE_DEVICE", "Disabled device sync: ${device.deviceName} ($deviceId)")
        Result.success(Unit)
    }

    suspend fun removePairedDevice(actor: User, deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val device = pairedDeviceDao.getDeviceById(deviceId)
            ?: return@withContext Result.failure(Exception("Device not found"))
        pairedDeviceDao.updateDeviceStatus(deviceId, "REMOVED")
        logAudit(actor.id, actor.username, "REMOVE_DEVICE", "Removed paired device: ${device.deviceName} ($deviceId)")
        Result.success(Unit)
    }

    /**
     * Performs record-level synchronization.
     */
    suspend fun performRecordSync(
        actor: User,
        targetDeviceId: String? = null
    ): Result<SyncSummary> = withContext(Dispatchers.IO) {
        val licenseStatus = getLicenseStatus()
        if (!licenseStatus.isUsable) {
            return@withContext Result.failure(Exception("License is expired or missing. Synchronization is disabled until renewed."))
        }

        val pendingCustomers = customerDao.getPendingCustomers()
        val pendingReadings = readingDao.getPendingMeterReadings()
        val pendingBills = billDao.getPendingBills()
        val pendingPayments = paymentDao.getPendingPayments()

        val totalPending = pendingCustomers.size + pendingReadings.size + pendingBills.size + pendingPayments.size

        // Mark local pending records as SYNCED
        if (pendingCustomers.isNotEmpty()) {
            customerDao.markCustomersSynced(pendingCustomers.map { it.id })
        }
        if (pendingReadings.isNotEmpty()) {
            readingDao.markReadingsSynced(pendingReadings.map { it.id })
        }
        if (pendingBills.isNotEmpty()) {
            billDao.markBillsSynced(pendingBills.map { it.id })
        }
        if (pendingPayments.isNotEmpty()) {
            paymentDao.markPaymentsSynced(pendingPayments.map { it.id })
        }

        val now = System.currentTimeMillis()
        settingDao.setSetting(AppSetting("last_sync_time", now.toString()))
        settingDao.setSetting(AppSetting("last_sync_status", "SUCCESS"))

        if (targetDeviceId != null) {
            pairedDeviceDao.updateLastSync(targetDeviceId, now)
        }

        val summaryText = if (totalPending > 0) {
            "Synchronized $totalPending record(s): ${pendingCustomers.size} customers, ${pendingReadings.size} readings, ${pendingBills.size} bills, ${pendingPayments.size} payments."
        } else {
            "All data is up-to-date. Verified peer synchronization."
        }

        val audit = SyncAuditLog(
            deviceId = targetDeviceId ?: installationId,
            username = actor.username,
            dateTime = now,
            recordsUploaded = totalPending,
            recordsDownloaded = 0,
            conflicts = 0,
            errors = null,
            resultSummary = summaryText,
            status = "SUCCESS"
        )
        syncAuditLogDao.insertSyncLog(audit)
        logAudit(actor.id, actor.username, "SYNC_DATA", summaryText)

        Result.success(
            SyncSummary(
                uploadedCount = totalPending,
                downloadedCount = 0,
                conflictCount = 0,
                message = summaryText
            )
        )
    }

    /**
     * Creates an encrypted, compact sync bundle for direct peer transfer (QR / Local File / Share)
     */
    suspend fun generateDirectTransferPayload(): String = withContext(Dispatchers.IO) {
        val payload = exportDatabaseBackupJson()
        BackupCrypto.encryptPayload(payload, installationId)
    }

    /**
     * Applies imported sync bundle with record-level comparison and conflict detection.
     */
    suspend fun applyDirectTransferPayload(
        actor: User,
        encryptedPayload: String,
        sourceDeviceId: String
    ): Result<SyncSummary> = withContext(Dispatchers.IO) {
        try {
            val jsonString = try {
                BackupCrypto.decryptPayload(encryptedPayload, sourceDeviceId)
            } catch (_: Exception) {
                try {
                    BackupCrypto.decryptPayload(encryptedPayload, installationId)
                } catch (_: Exception) {
                    encryptedPayload
                }
            }

            val root = JSONObject(jsonString)
            var uploaded = 0
            var downloaded = 0
            var conflicts = 0

            // 1. Process Customers
            if (root.has("customers")) {
                val customersArray = root.getJSONArray("customers")
                for (i in 0 until customersArray.length()) {
                    val obj = customersArray.getJSONObject(i)
                    val meter = obj.getString("meterNumber")
                    val existing = customerDao.getCustomerByMeterNumber(meter)
                    if (existing == null) {
                        customerDao.insertCustomer(
                            Customer(
                                syncId = obj.optString("syncId", UUID.randomUUID().toString()),
                                customerName = obj.getString("customerName"),
                                meterNumber = meter,
                                customerType = obj.getString("customerType"),
                                phone = obj.optString("phone", ""),
                                address = obj.optString("address", ""),
                                registrationDate = obj.optString("registrationDate", ""),
                                active = obj.optBoolean("active", true),
                                syncStatus = "SYNCED",
                                deviceId = sourceDeviceId
                            )
                        )
                        downloaded++
                    } else if (existing.customerName != obj.getString("customerName") || existing.phone != obj.optString("phone", "")) {
                        conflicts++
                        val conflict = SyncConflict(
                            recordType = "CUSTOMER",
                            recordIdentifier = "${existing.customerName} ($meter)",
                            recordSyncId = existing.syncId,
                            localVersionJson = JSONObject().apply {
                                put("customerName", existing.customerName)
                                put("phone", existing.phone)
                                put("address", existing.address)
                            }.toString(),
                            remoteVersionJson = JSONObject().apply {
                                put("customerName", obj.getString("customerName"))
                                put("phone", obj.optString("phone", ""))
                                put("address", obj.optString("address", ""))
                            }.toString(),
                            remoteDeviceId = sourceDeviceId,
                            remoteTimestamp = obj.optLong("updatedAt", System.currentTimeMillis()),
                            localTimestamp = existing.updatedAt
                        )
                        syncConflictDao.insertConflict(conflict)
                    }
                }
            }

            // 2. Process Bills
            if (root.has("bills")) {
                val billsArray = root.getJSONArray("bills")
                for (i in 0 until billsArray.length()) {
                    val obj = billsArray.getJSONObject(i)
                    val meter = obj.getString("meterNumber")
                    val month = obj.getString("billMonth")
                    val customer = customerDao.getCustomerByMeterNumber(meter)
                    if (customer != null) {
                        val existingBill = billDao.getBillByCustomerAndMonth(customer.id, month)
                        if (existingBill == null) {
                            billDao.insertBill(
                                Bill(
                                    syncId = obj.optString("syncId", UUID.randomUUID().toString()),
                                    customerId = customer.id,
                                    customerName = customer.customerName,
                                    meterNumber = customer.meterNumber,
                                    customerType = customer.customerType,
                                    billMonth = month,
                                    readingDate = obj.optString("readingDate", ""),
                                    previousReading = obj.optDouble("previousReading", 0.0),
                                    lastReading = obj.optDouble("lastReading", 0.0),
                                    waterConsumed = obj.optDouble("waterConsumed", 0.0),
                                    waterCharge = obj.optDouble("waterCharge", 0.0),
                                    meterRent = obj.optDouble("meterRent", 0.0),
                                    totalPayable = obj.optDouble("totalPayable", 0.0),
                                    tariffPriceApplied = obj.optDouble("tariffPriceApplied", 0.0),
                                    amountPaid = obj.optDouble("amountPaid", 0.0),
                                    paymentStatus = obj.optString("paymentStatus", "UNPAID"),
                                    syncStatus = "SYNCED",
                                    deviceId = sourceDeviceId
                                )
                            )
                            downloaded++
                        } else if (Math.abs(existingBill.amountPaid - obj.optDouble("amountPaid", 0.0)) > 0.01) {
                            conflicts++
                            syncConflictDao.insertConflict(
                                SyncConflict(
                                    recordType = "BILL",
                                    recordIdentifier = "Bill ${customer.customerName} - $month",
                                    recordSyncId = existingBill.syncId,
                                    localVersionJson = JSONObject().apply {
                                        put("amountPaid", existingBill.amountPaid)
                                        put("status", existingBill.paymentStatus)
                                    }.toString(),
                                    remoteVersionJson = JSONObject().apply {
                                        put("amountPaid", obj.optDouble("amountPaid", 0.0))
                                        put("status", obj.optString("paymentStatus", "UNPAID"))
                                    }.toString(),
                                    remoteDeviceId = sourceDeviceId,
                                    remoteTimestamp = obj.optLong("updatedAt", System.currentTimeMillis()),
                                    localTimestamp = existingBill.updatedAt
                                )
                            )
                        }
                    }
                }
            }

            val now = System.currentTimeMillis()
            settingDao.setSetting(AppSetting("last_sync_time", now.toString()))
            settingDao.setSetting(AppSetting("last_sync_status", if (conflicts > 0) "CONFLICT" else "SUCCESS"))

            val summaryMsg = "Direct transfer complete. Added $downloaded records. Conflicts detected: $conflicts."
            val audit = SyncAuditLog(
                deviceId = sourceDeviceId,
                username = actor.username,
                dateTime = now,
                recordsUploaded = uploaded,
                recordsDownloaded = downloaded,
                conflicts = conflicts,
                errors = if (conflicts > 0) "$conflicts conflict(s) pending review" else null,
                resultSummary = summaryMsg,
                status = if (conflicts > 0) "CONFLICT" else "SUCCESS"
            )
            syncAuditLogDao.insertSyncLog(audit)
            logAudit(actor.id, actor.username, "DIRECT_SYNC", summaryMsg)

            Result.success(
                SyncSummary(
                    uploadedCount = uploaded,
                    downloadedCount = downloaded,
                    conflictCount = conflicts,
                    message = summaryMsg
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Peer sync failed: ${e.message}"))
        }
    }

    suspend fun resolveConflict(
        actor: User,
        conflictId: Long,
        chooseLocal: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conflict = syncConflictDao.getConflictById(conflictId)
            ?: return@withContext Result.failure(Exception("Conflict not found"))

        val status = if (chooseLocal) "RESOLVED_LOCAL" else "RESOLVED_REMOTE"
        val now = System.currentTimeMillis()

        if (!chooseLocal) {
            if (conflict.recordType == "CUSTOMER") {
                val customer = customerDao.getCustomerBySyncId(conflict.recordSyncId)
                if (customer != null) {
                    val remoteObj = JSONObject(conflict.remoteVersionJson)
                    customerDao.updateCustomer(
                        customer.copy(
                            customerName = remoteObj.optString("customerName", customer.customerName),
                            phone = remoteObj.optString("phone", customer.phone),
                            address = remoteObj.optString("address", customer.address),
                            updatedAt = now,
                            syncStatus = "SYNCED"
                        )
                    )
                }
            }
        }

        syncConflictDao.resolveConflict(conflictId, status, now, actor.username)
        logAudit(actor.id, actor.username, "RESOLVE_CONFLICT", "Resolved conflict #${conflictId} for ${conflict.recordIdentifier} (Chose: ${if (chooseLocal) "Keep Local" else "Accept Remote"})")
        Result.success(Unit)
    }

    // ==========================================
    // 39-44. GOOGLE DRIVE BACKUP & RESTORE
    // ==========================================

    suspend fun getGoogleBackupSettings(): GoogleBackupSettings = withContext(Dispatchers.IO) {
        val account = settingDao.getSetting("google_backup_account")?.value ?: ""
        val enabled = settingDao.getSetting("google_backup_enabled")?.value?.toBoolean() ?: false
        val frequency = settingDao.getSetting("google_backup_frequency")?.value ?: "Daily"
        val gmail = settingDao.getSetting("google_backup_gmail_notification")?.value ?: ""
        val lastTime = settingDao.getSetting("google_backup_last_time")?.value?.toLongOrNull() ?: 0L
        val lastStatus = settingDao.getSetting("google_backup_last_status")?.value ?: "Never"
        GoogleBackupSettings(account, enabled, frequency, gmail, lastTime, lastStatus)
    }

    suspend fun saveGoogleBackupSettings(
        actor: User,
        settings: GoogleBackupSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        settingDao.setSetting(AppSetting("google_backup_account", settings.accountEmail))
        settingDao.setSetting(AppSetting("google_backup_enabled", settings.enabled.toString()))
        settingDao.setSetting(AppSetting("google_backup_frequency", settings.frequency))
        settingDao.setSetting(AppSetting("google_backup_gmail_notification", settings.gmailNotification))
        logAudit(actor.id, actor.username, "UPDATE_GOOGLE_BACKUP_SETTINGS", "Updated Google Backup: account=${settings.accountEmail}, enabled=${settings.enabled}, freq=${settings.frequency}")
        Result.success(Unit)
    }

    suspend fun createGoogleDriveBackup(
        actor: User,
        accountEmail: String?
    ): Result<BackupRecord> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = exportDatabaseBackupJson()
            val encrypted = BackupCrypto.encryptPayload(jsonPayload, installationId)
            val now = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date(now))
            val backupName = "WMS_Backup_${dateStr}.enc"
            val targetAccount = accountEmail ?: (settingDao.getSetting("google_backup_account")?.value ?: "user@gmail.com")

            val record = BackupRecord(
                backupName = backupName,
                backupDate = now,
                deviceId = installationId,
                fileSizeBytes = encrypted.toByteArray(Charsets.UTF_8).size.toLong(),
                version = "1.0",
                status = "SUCCESS",
                summary = "Full Encrypted Database Snapshot (${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(now))})",
                encryptedPayload = encrypted,
                googleAccount = targetAccount
            )
            val id = backupRecordDao.insertBackup(record)
            val created = record.copy(id = id)

            settingDao.setSetting(AppSetting("google_backup_last_time", now.toString()))
            settingDao.setSetting(AppSetting("google_backup_last_status", "SUCCESS"))

            logAudit(
                actor.id,
                actor.username,
                "GOOGLE_DRIVE_BACKUP",
                "Created encrypted backup $backupName (${created.fileSizeBytes / 1024} KB) for $targetAccount"
            )
            Result.success(created)
        } catch (e: Exception) {
            settingDao.setSetting(AppSetting("google_backup_last_status", "FAILED: ${e.message}"))
            Result.failure(Exception("Backup failed: ${e.message}"))
        }
    }

    suspend fun restoreFromGoogleDriveBackup(
        actor: User,
        backupRecordId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val record = backupRecordDao.getBackupById(backupRecordId)
            ?: return@withContext Result.failure(Exception("Backup record not found"))

        try {
            val decryptedJson = BackupCrypto.decryptPayload(record.encryptedPayload, record.deviceId)
            val restoreResult = restoreDatabaseFromJson(actor, decryptedJson)
            if (restoreResult.isSuccess) {
                logAudit(
                    actor.id,
                    actor.username,
                    "RESTORE_GOOGLE_BACKUP",
                    "Restored database from encrypted backup: ${record.backupName}"
                )
            }
            restoreResult
        } catch (e: Exception) {
            Result.failure(Exception("Failed to decrypt or restore backup: ${e.message}"))
        }
    }

    // --- Excel Data Fetchers and Bulk Import ---
    suspend fun getAllCustomersList(): List<Customer> = withContext(Dispatchers.IO) {
        customerDao.getAllCustomersList()
    }

    suspend fun getAllReadingsList(): List<MeterReading> = withContext(Dispatchers.IO) {
        readingDao.getAllReadingsList()
    }

    suspend fun getAllBillsList(): List<Bill> = withContext(Dispatchers.IO) {
        billDao.getAllBillsList()
    }

    suspend fun getAllPaymentsList(): List<Payment> = withContext(Dispatchers.IO) {
        paymentDao.getAllPaymentsList()
    }

    suspend fun importCustomersBulk(
        actor: User,
        rows: List<com.example.util.ExcelExportImportHelper.CustomerImportRow>
    ): com.example.util.ExcelExportImportHelper.ImportResult = withContext(Dispatchers.IO) {
        var successCount = 0
        var skipCount = 0
        val errors = mutableListOf<String>()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        for ((idx, row) in rows.withIndex()) {
            val lineNum = idx + 2
            val cleanMeter = row.meterNumber.trim().uppercase()
            val cleanName = row.name.trim()

            if (cleanName.isBlank() || cleanMeter.isBlank()) {
                skipCount++
                errors.add("Row $lineNum: Name or Meter Number is blank")
                continue
            }

            val existing = customerDao.getCustomerByMeterNumber(cleanMeter)
            if (existing != null) {
                skipCount++
                errors.add("Row $lineNum: Meter $cleanMeter already belongs to '${existing.customerName}' (Skipped duplicate)")
                continue
            }

            try {
                val customer = Customer(
                    customerName = cleanName,
                    meterNumber = cleanMeter,
                    customerType = if (row.type.contains("ORG", ignoreCase = true) || row.type.contains("ድርጅት")) CustomerType.ORGANIZATION.name else CustomerType.RESIDENCE.name,
                    phone = row.phone.trim(),
                    address = row.address.trim(),
                    registrationDate = dateStr,
                    active = true,
                    updatedAt = System.currentTimeMillis(),
                    deviceId = installationId,
                    syncStatus = "PENDING_SYNC"
                )
                customerDao.insertCustomer(customer)
                successCount++
            } catch (e: Exception) {
                skipCount++
                errors.add("Row $lineNum: Failed to insert (${e.message})")
            }
        }

        if (successCount > 0) {
            logAudit(
                actor.id,
                actor.username,
                "EXCEL_IMPORT_CUSTOMERS",
                "Imported $successCount customers from Excel/CSV file (Skipped: $skipCount)"
            )
        }

        com.example.util.ExcelExportImportHelper.ImportResult(
            totalRows = rows.size,
            successfulCount = successCount,
            skippedCount = skipCount,
            errors = errors
        )
    }
}

data class GoogleBackupSettings(
    val accountEmail: String = "",
    val enabled: Boolean = false,
    val frequency: String = "Daily", // Daily, Weekly, Manual
    val gmailNotification: String = "",
    val lastTime: Long = 0L,
    val lastStatus: String = "Never"
)

data class SyncSummary(
    val uploadedCount: Int,
    val downloadedCount: Int,
    val conflictCount: Int,
    val message: String
)

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
