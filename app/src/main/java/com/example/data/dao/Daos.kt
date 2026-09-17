package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE username = :username AND active = 1 LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): User?

    @Query("SELECT COUNT(*) FROM users WHERE role = 'DEVELOPER'")
    suspend fun getDeveloperCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE users SET passwordHash = :hash, salt = :salt, updatedAt = :timestamp WHERE id = :userId")
    suspend fun resetPassword(userId: Long, hash: String, salt: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE users SET active = :active, updatedAt = :timestamp WHERE id = :userId")
    suspend fun setUserActive(userId: Long, active: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Long)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY id DESC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers ORDER BY id ASC")
    suspend fun getAllCustomersList(): List<Customer>

    @Query("SELECT * FROM customers WHERE active = 1 ORDER BY customerName ASC")
    fun getActiveCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): Customer?

    @Query("SELECT * FROM customers WHERE meterNumber = :meterNumber LIMIT 1")
    suspend fun getCustomerByMeterNumber(meterNumber: String): Customer?

    @Query("""
        SELECT * FROM customers 
        WHERE customerName LIKE '%' || :query || '%' 
           OR meterNumber LIKE '%' || :query || '%' 
           OR phone LIKE '%' || :query || '%' 
           OR CAST(id AS TEXT) LIKE '%' || :query || '%'
        ORDER BY customerName ASC
    """)
    fun searchCustomers(query: String): Flow<List<Customer>>

    @Query("SELECT COUNT(*) FROM customers")
    fun getTotalCustomersCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM customers WHERE active = 1")
    fun getActiveCustomersCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Query("UPDATE customers SET active = :active, updatedAt = :timestamp, syncStatus = 'PENDING_SYNC' WHERE id = :customerId")
    suspend fun setCustomerActive(customerId: Long, active: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM customers WHERE syncStatus = 'PENDING_SYNC'")
    fun getPendingCustomersCount(): Flow<Int>

    @Query("SELECT * FROM customers WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingCustomers(): List<Customer>

    @Query("UPDATE customers SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markCustomersSynced(ids: List<Long>)

    @Query("SELECT * FROM customers WHERE syncId = :syncId LIMIT 1")
    suspend fun getCustomerBySyncId(syncId: String): Customer?
}

@Dao
interface MeterReadingDao {
    @Query("SELECT * FROM meter_readings ORDER BY id DESC")
    fun getAllReadings(): Flow<List<MeterReading>>

    @Query("SELECT * FROM meter_readings ORDER BY id ASC")
    suspend fun getAllReadingsList(): List<MeterReading>

    @Query("SELECT * FROM meter_readings WHERE customerId = :customerId ORDER BY id DESC")
    fun getReadingsForCustomer(customerId: Long): Flow<List<MeterReading>>

    @Query("SELECT * FROM meter_readings WHERE customerId = :customerId ORDER BY id DESC LIMIT 1")
    suspend fun getLatestReadingForCustomer(customerId: Long): MeterReading?

    @Query("SELECT * FROM meter_readings WHERE readingDate = :date")
    fun getReadingsByDate(date: String): Flow<List<MeterReading>>

    @Query("SELECT COUNT(*) FROM meter_readings WHERE readingDate = :date")
    fun getCountForDate(date: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: MeterReading): Long

    @Query("SELECT COUNT(*) FROM meter_readings WHERE syncStatus = 'PENDING_SYNC'")
    fun getPendingReadingsCount(): Flow<Int>

    @Query("SELECT * FROM meter_readings WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingMeterReadings(): List<MeterReading>

    @Query("UPDATE meter_readings SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markReadingsSynced(ids: List<Long>)

    @Query("SELECT * FROM meter_readings WHERE syncId = :syncId LIMIT 1")
    suspend fun getReadingBySyncId(syncId: String): MeterReading?
}

@Dao
interface TariffDao {
    @Query("SELECT * FROM tariffs ORDER BY customerType ASC, minUsage ASC")
    fun getAllTariffs(): Flow<List<Tariff>>

    @Query("SELECT * FROM tariffs WHERE customerType = :type AND active = 1 ORDER BY minUsage ASC")
    fun getActiveTariffsForType(type: String): Flow<List<Tariff>>

    @Query("SELECT * FROM tariffs WHERE customerType = :type AND active = 1 AND :usage >= minUsage AND :usage <= maxUsage LIMIT 1")
    suspend fun findMatchingTariff(type: String, usage: Double): Tariff?

    @Query("SELECT * FROM tariffs WHERE id = :id LIMIT 1")
    suspend fun getTariffById(id: Long): Tariff?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTariff(tariff: Tariff): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tariffs: List<Tariff>)

    @Update
    suspend fun updateTariff(tariff: Tariff)

    @Delete
    suspend fun deleteTariff(tariff: Tariff)
}

@Dao
interface MeterRentDao {
    @Query("SELECT * FROM meter_rent ORDER BY customerType ASC")
    fun getAllMeterRents(): Flow<List<MeterRent>>

    @Query("SELECT * FROM meter_rent WHERE customerType = :type AND active = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveMeterRent(type: String): MeterRent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeterRent(rent: MeterRent): Long

    @Update
    suspend fun updateMeterRent(rent: MeterRent)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY id DESC")
    fun getAllBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY id ASC")
    suspend fun getAllBillsList(): List<Bill>

    @Query("SELECT * FROM bills WHERE customerId = :customerId ORDER BY id DESC")
    fun getBillsForCustomer(customerId: Long): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun getBillById(id: Long): Bill?

    @Query("SELECT * FROM bills WHERE customerId = :customerId AND billMonth = :month LIMIT 1")
    suspend fun getBillByCustomerAndMonth(customerId: Long, month: String): Bill?

    @Query("SELECT * FROM bills WHERE billMonth = :month ORDER BY id DESC")
    fun getBillsByMonth(month: String): Flow<List<Bill>>

    @Query("""
        SELECT * FROM bills 
        WHERE customerName LIKE '%' || :query || '%' 
           OR meterNumber LIKE '%' || :query || '%' 
           OR billMonth LIKE '%' || :query || '%'
        ORDER BY id DESC
    """)
    fun searchBills(query: String): Flow<List<Bill>>

    @Query("SELECT COUNT(*) FROM bills WHERE billMonth = :month")
    fun getBillsCountThisMonth(month: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM bills WHERE paymentStatus = 'PAID'")
    fun getPaidBillsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM bills WHERE paymentStatus != 'PAID'")
    fun getUnpaidBillsCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalPayable), 0.0) FROM bills WHERE billMonth = :month")
    fun getTotalBilledThisMonth(month: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(amountPaid), 0.0) FROM bills WHERE billMonth = :month")
    fun getTotalCollectedThisMonth(month: String): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBill(bill: Bill): Long

    @Update
    suspend fun updateBill(bill: Bill)

    @Query("UPDATE bills SET amountPaid = :amountPaid, paymentStatus = :status WHERE id = :billId")
    suspend fun updatePaymentStatus(billId: Long, amountPaid: Double, status: String)

    @Query("SELECT COUNT(*) FROM bills WHERE syncStatus = 'PENDING_SYNC'")
    fun getPendingBillsCount(): Flow<Int>

    @Query("SELECT * FROM bills WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingBills(): List<Bill>

    @Query("UPDATE bills SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markBillsSynced(ids: List<Long>)

    @Query("SELECT * FROM bills WHERE syncId = :syncId LIMIT 1")
    suspend fun getBillBySyncId(syncId: String): Bill?
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY id DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY id ASC")
    suspend fun getAllPaymentsList(): List<Payment>

    @Query("SELECT * FROM payments WHERE billId = :billId ORDER BY id DESC")
    fun getPaymentsForBill(billId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE paymentDate = :date ORDER BY id DESC")
    fun getPaymentsByDate(date: String): Flow<List<Payment>>

    @Query("SELECT COALESCE(SUM(amountPaid), 0.0) FROM payments WHERE paymentDate = :date")
    fun getDailyCollection(date: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(amountPaid), 0.0) FROM payments WHERE billMonth = :month")
    fun getMonthlyCollection(month: String): Flow<Double>

    @Query("SELECT COUNT(*) FROM payments")
    suspend fun getPaymentsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Query("SELECT COUNT(*) FROM payments WHERE syncStatus = 'PENDING_SYNC'")
    fun getPendingPaymentsCount(): Flow<Int>

    @Query("SELECT * FROM payments WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingPayments(): List<Payment>

    @Query("UPDATE payments SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markPaymentsSynced(ids: List<Long>)

    @Query("SELECT * FROM payments WHERE syncId = :syncId LIMIT 1")
    suspend fun getPaymentBySyncId(syncId: String): Payment?
}

@Dao
interface LicenseDao {
    @Query("SELECT * FROM licenses ORDER BY id DESC LIMIT 1")
    fun getLatestLicense(): Flow<License?>

    @Query("SELECT * FROM licenses ORDER BY id DESC LIMIT 1")
    suspend fun getLatestLicenseSync(): License?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLicense(license: License): Long

    @Update
    suspend fun updateLicense(license: License)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY dateTime DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 200): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog): Long

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): AppSetting?

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    fun getSettingFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSetting)
}

@Dao
interface PairedDeviceDao {
    @Query("SELECT * FROM paired_devices ORDER BY createdAt DESC")
    fun getAllPairedDevices(): Flow<List<PairedDevice>>

    @Query("SELECT * FROM paired_devices WHERE status != 'REMOVED' ORDER BY createdAt DESC")
    fun getActivePairedDevices(): Flow<List<PairedDevice>>

    @Query("SELECT * FROM paired_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): PairedDevice?

    @Query("SELECT * FROM paired_devices WHERE pairingCode = :code AND pairingCodeExpiry > :currentTime LIMIT 1")
    suspend fun getDeviceByValidPairingCode(code: String, currentTime: Long = System.currentTimeMillis()): PairedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: PairedDevice): Long

    @Update
    suspend fun updateDevice(device: PairedDevice)

    @Query("UPDATE paired_devices SET status = :status WHERE deviceId = :deviceId")
    suspend fun updateDeviceStatus(deviceId: String, status: String)

    @Query("UPDATE paired_devices SET lastSyncTimestamp = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastSync(deviceId: String, timestamp: Long)

    @Query("DELETE FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)
}

@Dao
interface SyncAuditLogDao {
    @Query("SELECT * FROM sync_audit_logs ORDER BY dateTime DESC LIMIT :limit")
    fun getRecentSyncLogs(limit: Int = 100): Flow<List<SyncAuditLog>>

    @Query("SELECT * FROM sync_audit_logs ORDER BY dateTime DESC LIMIT 1")
    fun getLatestSyncLog(): Flow<SyncAuditLog?>

    @Query("SELECT * FROM sync_audit_logs ORDER BY dateTime DESC LIMIT 1")
    suspend fun getLatestSyncLogDirect(): SyncAuditLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncAuditLog): Long
}

@Dao
interface SyncConflictDao {
    @Query("SELECT * FROM sync_conflicts WHERE status = 'PENDING' ORDER BY remoteTimestamp DESC")
    fun getPendingConflicts(): Flow<List<SyncConflict>>

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE status = 'PENDING'")
    fun getPendingConflictCount(): Flow<Int>

    @Query("SELECT * FROM sync_conflicts WHERE id = :id LIMIT 1")
    suspend fun getConflictById(id: Long): SyncConflict?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflict): Long

    @Query("UPDATE sync_conflicts SET status = :status, resolvedAt = :resolvedAt, resolvedBy = :resolvedBy WHERE id = :id")
    suspend fun resolveConflict(id: Long, status: String, resolvedAt: Long, resolvedBy: String)
}

@Dao
interface BackupRecordDao {
    @Query("SELECT * FROM backup_records ORDER BY backupDate DESC")
    fun getAllBackups(): Flow<List<BackupRecord>>

    @Query("SELECT * FROM backup_records WHERE id = :id LIMIT 1")
    suspend fun getBackupById(id: Long): BackupRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: BackupRecord): Long

    @Query("DELETE FROM backup_records WHERE id = :id")
    suspend fun deleteBackup(id: Long)
}

