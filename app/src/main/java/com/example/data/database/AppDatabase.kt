package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.model.*
import com.example.security.PasswordHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        User::class,
        Customer::class,
        MeterReading::class,
        Tariff::class,
        MeterRent::class,
        Bill::class,
        Payment::class,
        License::class,
        AuditLog::class,
        AppSetting::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun meterReadingDao(): MeterReadingDao
    abstract fun tariffDao(): TariffDao
    abstract fun meterRentDao(): MeterRentDao
    abstract fun billDao(): BillDao
    abstract fun paymentDao(): PaymentDao
    abstract fun licenseDao(): LicenseDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "water_management_database.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val userDao = database.userDao()
            val tariffDao = database.tariffDao()
            val meterRentDao = database.meterRentDao()
            val customerDao = database.customerDao()
            val appSettingDao = database.appSettingDao()

            // 1. App name setting
            appSettingDao.setSetting(AppSetting("app_name", "WATER MANAGEMENT SYSTEM"))
            appSettingDao.setSetting(AppSetting("initial_setup_completed", "false"))

            // 2. Default Developer account (Default password: MomLove@1)
            val devSalt = PasswordHasher.generateSalt()
            val devHash = PasswordHasher.hashPassword("MomLove@1", devSalt)
            userDao.insertUser(
                User(
                    username = "developer",
                    passwordHash = devHash,
                    salt = devSalt,
                    role = UserRole.DEVELOPER.name,
                    active = true
                )
            )

            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // 3. Default Tariffs for Residence (መኖርያ)
            val residenceRanges = listOf(
                Triple(1.0, 5.0, 15.0),
                Triple(6.0, 10.0, 20.0),
                Triple(11.0, 15.0, 25.0),
                Triple(16.0, 20.0, 30.0),
                Triple(21.0, 25.0, 35.0),
                Triple(26.0, 30.0, 40.0),
                Triple(31.0, 35.0, 45.0),
                Triple(36.0, 40.0, 50.0),
                Triple(41.0, 45.0, 55.0),
                Triple(46.0, 50.0, 60.0),
                Triple(51.0, 55.0, 65.0),
                Triple(56.0, 60.0, 70.0),
                Triple(61.0, 65.0, 75.0),
                Triple(66.0, 70.0, 80.0),
                Triple(71.0, 75.0, 85.0),
                Triple(76.0, 80.0, 90.0),
                Triple(81.0, 85.0, 95.0),
                Triple(86.0, 99999.0, 110.0)
            )
            for ((min, max, price) in residenceRanges) {
                tariffDao.insertTariff(
                    Tariff(
                        customerType = CustomerType.RESIDENCE.name,
                        minUsage = min,
                        maxUsage = max,
                        price = price,
                        effectiveDate = currentDate,
                        active = true
                    )
                )
            }

            // 4. Default Tariffs for Organization (ድርጅት)
            val orgRanges = listOf(
                Triple(1.0, 5.0, 25.0),
                Triple(6.0, 10.0, 35.0),
                Triple(11.0, 15.0, 45.0),
                Triple(16.0, 20.0, 55.0),
                Triple(21.0, 25.0, 65.0),
                Triple(26.0, 30.0, 75.0),
                Triple(31.0, 35.0, 85.0),
                Triple(36.0, 40.0, 95.0),
                Triple(41.0, 45.0, 105.0),
                Triple(46.0, 50.0, 115.0),
                Triple(51.0, 55.0, 125.0),
                Triple(56.0, 60.0, 135.0),
                Triple(61.0, 65.0, 145.0),
                Triple(66.0, 70.0, 155.0),
                Triple(71.0, 75.0, 165.0),
                Triple(76.0, 80.0, 175.0),
                Triple(81.0, 85.0, 185.0),
                Triple(86.0, 99999.0, 200.0)
            )
            for ((min, max, price) in orgRanges) {
                tariffDao.insertTariff(
                    Tariff(
                        customerType = CustomerType.ORGANIZATION.name,
                        minUsage = min,
                        maxUsage = max,
                        price = price,
                        effectiveDate = currentDate,
                        active = true
                    )
                )
            }

            // 5. Default Meter Rent
            meterRentDao.insertMeterRent(
                MeterRent(
                    customerType = CustomerType.RESIDENCE.name,
                    amount = 30.0,
                    effectiveDate = currentDate,
                    active = true
                )
            )
            meterRentDao.insertMeterRent(
                MeterRent(
                    customerType = CustomerType.ORGANIZATION.name,
                    amount = 60.0,
                    effectiveDate = currentDate,
                    active = true
                )
            )

            // 6. Sample Initial Customers for quick testing
            customerDao.insertCustomer(
                Customer(
                    customerName = "Abebe Kebede",
                    meterNumber = "MTR-001",
                    customerType = CustomerType.RESIDENCE.name,
                    phone = "+251911223344",
                    address = "Kebele 04, House 120",
                    registrationDate = currentDate,
                    active = true
                )
            )
            customerDao.insertCustomer(
                Customer(
                    customerName = "Almaz Tesfay",
                    meterNumber = "MTR-002",
                    customerType = CustomerType.RESIDENCE.name,
                    phone = "+251922334455",
                    address = "Kebele 02, House 45",
                    registrationDate = currentDate,
                    active = true
                )
            )
            customerDao.insertCustomer(
                Customer(
                    customerName = "Nile Commercial Plaza",
                    meterNumber = "MTR-ORG-101",
                    customerType = CustomerType.ORGANIZATION.name,
                    phone = "+251933445566",
                    address = "Main Street, Building 8",
                    registrationDate = currentDate,
                    active = true
                )
            )
        }
    }
}
