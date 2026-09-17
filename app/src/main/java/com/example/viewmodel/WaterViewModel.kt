package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BillCalculationPreview
import com.example.data.repository.LicenseStatusInfo
import com.example.data.repository.WaterRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen(val title: String) {
    DEVELOPER_SETUP("Developer Activation"),
    LOGIN("Login"),
    DASHBOARD("Dashboard"),
    CUSTOMERS("Customers"),
    READINGS("Meter Readings"),
    BILLS("Bills"),
    PAYMENTS("Payments & Receipts"),
    TARIFFS("Tariff & Meter Rent"),
    REPORTS("Reports"),
    USERS("User Management"),
    DEVELOPER_SETTINGS("System Settings")
}

class WaterViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    val repository = WaterRepository(database, application)

    // Current screen navigation
    private val _currentScreen = MutableStateFlow(AppScreen.LOGIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Session
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // First setup check
    private val _isInitialSetupCompleted = MutableStateFlow(false)
    val isInitialSetupCompleted: StateFlow<Boolean> = _isInitialSetupCompleted.asStateFlow()

    // App Name
    val appName: StateFlow<String> = repository.getAppNameFlow()
        .map { it ?: "WATER MANAGEMENT SYSTEM" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "WATER MANAGEMENT SYSTEM")

    // License Info
    private val _licenseInfo = MutableStateFlow(
        LicenseStatusInfo(license = null, status = "EXPIRED", remainingDays = 0, isUsable = false)
    )
    val licenseInfo: StateFlow<LicenseStatusInfo> = _licenseInfo.asStateFlow()

    // Notification / Error banners
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Search and filter states
    val customerSearchQuery = MutableStateFlow("")
    val billSearchQuery = MutableStateFlow("")
    val billStatusFilter = MutableStateFlow("ALL") // ALL, PAID, UNPAID, PARTIALLY PAID

    // Customers
    val customers: StateFlow<List<Customer>> = customerSearchQuery
        .debounce(200)
        .flatMapLatest { query ->
            if (query.isBlank()) repository.allCustomers else repository.searchCustomers(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCustomersCount = repository.totalCustomersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeCustomersCount = repository.activeCustomersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Tariffs
    val allTariffs: StateFlow<List<Tariff>> = repository.allTariffs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMeterRents: StateFlow<List<MeterRent>> = repository.allMeterRents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bills
    val bills: StateFlow<List<Bill>> = combine(
        billSearchQuery.debounce(200),
        billStatusFilter,
        repository.allBills
    ) { query, filter, list ->
        list.filter { bill ->
            val matchesQuery = query.isBlank() ||
                    bill.customerName.contains(query, ignoreCase = true) ||
                    bill.meterNumber.contains(query, ignoreCase = true) ||
                    bill.billMonth.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                "PAID" -> bill.paymentStatus == "PAID"
                "UNPAID" -> bill.paymentStatus == "UNPAID"
                "PARTIALLY PAID" -> bill.paymentStatus == "PARTIALLY PAID"
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Payments
    val payments: StateFlow<List<Payment>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Users
    val allUsers: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Audit logs
    val recentAuditLogs: StateFlow<List<AuditLog>> = repository.recentAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current Bill Month string (e.g. "September 2026")
    val currentMonthFormatted: String = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date())
    val currentDateFormatted: String = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

    // Active receipt preview dialog
    private val _activeReceiptPayment = MutableStateFlow<Payment?>(null)
    val activeReceiptPayment: StateFlow<Payment?> = _activeReceiptPayment.asStateFlow()

    init {
        checkInitialSetup()
        refreshLicenseStatus()
    }

    fun clearMessages() {
        _uiMessage.value = null
        _errorMessage.value = null
    }

    fun showMessage(msg: String) {
        _uiMessage.value = msg
    }

    fun showError(err: String) {
        _errorMessage.value = err
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun checkInitialSetup() {
        viewModelScope.launch {
            val isDone = repository.isInitialSetupCompleted()
            _isInitialSetupCompleted.value = isDone
            if (!isDone) {
                _currentScreen.value = AppScreen.DEVELOPER_SETUP
            }
        }
    }

    fun refreshLicenseStatus() {
        viewModelScope.launch {
            _licenseInfo.value = repository.getLicenseStatus()
        }
    }

    // --- Authentication Actions ---
    fun performLogin(username: String, password: String) {
        viewModelScope.launch {
            clearMessages()
            val result = repository.login(username, password)
            result.onSuccess { user ->
                _currentUser.value = user
                refreshLicenseStatus()

                // Direct to permitted screen based on role
                when (user.role) {
                    UserRole.READING_USER.name -> _currentScreen.value = AppScreen.READINGS
                    UserRole.ACCOUNTANT.name -> _currentScreen.value = AppScreen.DASHBOARD
                    UserRole.ADMIN.name -> _currentScreen.value = AppScreen.DASHBOARD
                    UserRole.DEVELOPER.name -> _currentScreen.value = AppScreen.DASHBOARD
                    else -> _currentScreen.value = AppScreen.DASHBOARD
                }
                showMessage("Welcome, ${user.username}!")
            }.onFailure { err ->
                showError(err.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                repository.logAudit(user.id, user.username, "LOGOUT", "User logged out")
            }
        }
        _currentUser.value = null
        _currentScreen.value = AppScreen.LOGIN
        showMessage("Logged out successfully")
    }

    // --- First Installation Developer Setup ---
    fun completeFirstTimeSetup(
        defaultDevPasswordInput: String,
        newDevPassword: String,
        adminUsername: String,
        adminPassword: String,
        licenseType: String
    ) {
        viewModelScope.launch {
            clearMessages()
            if (defaultDevPasswordInput != "MomLove@1") {
                showError("Invalid default Developer password")
                return@launch
            }
            if (newDevPassword.length < 6) {
                showError("New Developer password must be at least 6 characters")
                return@launch
            }
            if (adminUsername.isBlank() || adminPassword.length < 4) {
                showError("Please enter valid Admin credentials")
                return@launch
            }

            // 1. Verify and update developer password
            val devUser = repository.allUsers.first().firstOrNull { it.role == UserRole.DEVELOPER.name }
            if (devUser == null) {
                showError("Developer account not found in database")
                return@launch
            }

            repository.resetPassword(devUser, devUser.id, newDevPassword)

            // 2. Create initial Admin account
            val adminResult = repository.createUser(
                actor = devUser,
                username = adminUsername.trim(),
                password = adminPassword,
                role = UserRole.ADMIN
            )
            if (adminResult.isFailure) {
                showError("Failed to create Admin: ${adminResult.exceptionOrNull()?.message}")
                return@launch
            }

            // 3. Activate selected license
            repository.activateLicense(devUser, licenseType)

            // 4. Mark setup as completed
            repository.setInitialSetupCompleted()
            _isInitialSetupCompleted.value = true

            refreshLicenseStatus()
            _currentScreen.value = AppScreen.LOGIN
            showMessage("Developer Setup completed successfully! Please login with your credentials.")
        }
    }

    // --- Customer Actions ---
    fun registerCustomer(
        name: String,
        meterNumber: String,
        type: CustomerType,
        phone: String,
        address: String,
        onSuccess: () -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.registerCustomer(user, name, meterNumber, type, phone, address)
            result.onSuccess {
                showMessage("Customer '$name' registered with Meter #$meterNumber")
                onSuccess()
            }.onFailure {
                showError(it.message ?: "Failed to register customer")
            }
        }
    }

    fun updateCustomer(customer: Customer, onSuccess: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.updateCustomer(user, customer)
            result.onSuccess {
                showMessage("Customer '${customer.customerName}' updated")
                onSuccess()
            }.onFailure {
                showError(it.message ?: "Failed to update customer")
            }
        }
    }

    fun toggleCustomerActive(customer: Customer) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val newActive = !customer.active
            val result = repository.setCustomerActive(user, customer.id, newActive)
            result.onSuccess {
                showMessage("${if (newActive) "Activated" else "Deactivated"} customer '${customer.customerName}'")
            }.onFailure {
                showError(it.message ?: "Failed to change customer status")
            }
        }
    }

    // --- Meter Reading & Bill Generation ---
    fun calculatePreview(
        customerId: Long,
        lastReading: Double,
        onResult: (Result<BillCalculationPreview>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.previewBillCalculation(customerId, lastReading)
            onResult(res)
        }
    }

    fun submitReadingAndBill(
        customerId: Long,
        lastReading: Double,
        billMonth: String,
        onSuccess: (Bill) -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.saveReadingAndGenerateBill(user, customerId, lastReading, billMonth)
            result.onSuccess { bill ->
                showMessage("Bill generated for ${bill.customerName}: ${bill.totalPayable} Birr")
                onSuccess(bill)
            }.onFailure {
                showError(it.message ?: "Failed to save reading")
            }
        }
    }

    // --- Payment Actions ---
    fun recordPayment(
        billId: Long,
        amountToPay: Double,
        onSuccess: (Payment) -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.recordPayment(user, billId, amountToPay)
            result.onSuccess { payment ->
                showMessage("Payment of ${payment.amountPaid} Birr recorded (Receipt #${payment.receiptNumber})")
                _activeReceiptPayment.value = payment
                onSuccess(payment)
            }.onFailure {
                showError(it.message ?: "Failed to record payment")
            }
        }
    }

    fun showReceipt(payment: Payment) {
        _activeReceiptPayment.value = payment
    }

    fun dismissReceipt() {
        _activeReceiptPayment.value = null
    }

    // --- Tariff & Meter Rent Actions ---
    fun addTariff(type: CustomerType, minUsage: Double, maxUsage: Double, price: Double, onSuccess: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.addTariff(user, type, minUsage, maxUsage, price)
            result.onSuccess {
                showMessage("Tariff tier added for ${type.displayName}")
                onSuccess()
            }.onFailure {
                showError(it.message ?: "Failed to add tariff")
            }
        }
    }

    fun updateTariff(tariff: Tariff, onSuccess: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.updateTariff(user, tariff)
            result.onSuccess {
                showMessage("Tariff tier updated")
                onSuccess()
            }.onFailure {
                showError(it.message ?: "Failed to update tariff")
            }
        }
    }

    fun deleteTariff(tariff: Tariff) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.deleteTariff(user, tariff)
            result.onSuccess {
                showMessage("Tariff tier removed")
            }.onFailure {
                showError(it.message ?: "Failed to delete tariff")
            }
        }
    }

    fun updateMeterRent(type: CustomerType, amount: Double) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.updateMeterRent(user, type, amount)
            result.onSuccess {
                showMessage("Meter rent for ${type.displayName} updated to $amount Birr")
            }.onFailure {
                showError(it.message ?: "Failed to update meter rent")
            }
        }
    }

    // --- Developer User Management Actions ---
    fun createNewUser(username: String, password: String, role: UserRole, onSuccess: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.createUser(user, username, password, role)
            result.onSuccess {
                showMessage("User '${username.trim()}' (${role.displayName}) created")
                onSuccess()
            }.onFailure {
                showError(it.message ?: "Failed to create user")
            }
        }
    }

    fun resetUserPassword(targetUserId: Long, newPass: String, onSuccess: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.resetPassword(user, targetUserId, newPass)
            result.onSuccess {
                showMessage("Password reset successfully")
                onSuccess()
            }.onFailure {
                showError(it.message ?: "Failed to reset password")
            }
        }
    }

    fun toggleUserStatus(targetUserId: Long, active: Boolean) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.toggleUserActive(user, targetUserId, active)
            result.onSuccess {
                showMessage("User status updated")
            }.onFailure {
                showError(it.message ?: "Failed to update user status")
            }
        }
    }

    // --- Developer System Settings Actions ---
    fun updateApplicationName(newName: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.setAppName(user, newName)
            result.onSuccess {
                showMessage("Application name updated to: $newName")
            }.onFailure {
                showError(it.message ?: "Failed to update application name")
            }
        }
    }

    fun activateLicense(licenseType: String) {
        val user = _currentUser.value
        viewModelScope.launch {
            clearMessages()
            val result = repository.activateLicense(user, licenseType)
            result.onSuccess {
                refreshLicenseStatus()
                showMessage("License activated for $licenseType successfully")
            }.onFailure {
                showError(it.message ?: "Failed to activate license")
            }
        }
    }

    fun resetAllData(onSuccess: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.resetAllApplicationData(user)
            result.onSuccess {
                showMessage("All application data has been reset to defaults")
                logout()
                onSuccess()
            }.onFailure {
                showError(it.message ?: "Failed to reset application data")
            }
        }
    }

    fun restoreBackup(jsonString: String, onSuccess: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            clearMessages()
            val result = repository.restoreDatabaseFromJson(user, jsonString)
            result.onSuccess {
                showMessage("Database restored successfully from backup")
                onSuccess()
            }.onFailure {
                showError(it.message ?: "Failed to restore backup")
            }
        }
    }
}
