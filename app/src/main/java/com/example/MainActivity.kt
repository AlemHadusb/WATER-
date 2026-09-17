package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UserRole
import com.example.ui.components.WaterTopAppBar
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.WaterBluePrimary
import com.example.viewmodel.AppScreen
import com.example.viewmodel.WaterViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                WaterManagementApp()
            }
        }
    }
}

data class NavigationItem(
    val screen: AppScreen,
    val label: String,
    val icon: ImageVector
)

@Composable
fun WaterManagementApp(viewModel: WaterViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isInitialSetupCompleted by viewModel.isInitialSetupCompleted.collectAsState()
    val appName by viewModel.appName.collectAsState()
    val licenseInfo by viewModel.licenseInfo.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar("Error: $it")
            viewModel.clearMessages()
        }
    }

    // Determine if user is in auth or setup flow
    if (!isInitialSetupCompleted) {
        DeveloperSetupScreen(viewModel = viewModel)
        return
    }

    if (currentUser == null) {
        LoginScreen(viewModel = viewModel)
        return
    }

    val userRole = currentUser?.role ?: ""

    // Compute navigation tabs based on user's authorized role
    val navigationItems = remember(userRole) {
        when (userRole) {
            UserRole.READING_USER.name -> listOf(
                NavigationItem(AppScreen.READINGS, "Readings", Icons.Default.Speed)
            )
            UserRole.ACCOUNTANT.name -> listOf(
                NavigationItem(AppScreen.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
                NavigationItem(AppScreen.BILLS, "Bills", Icons.Default.Receipt),
                NavigationItem(AppScreen.PAYMENTS, "Payments", Icons.Default.Payment),
                NavigationItem(AppScreen.REPORTS, "Reports", Icons.Default.Assessment)
            )
            UserRole.ADMIN.name -> listOf(
                NavigationItem(AppScreen.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
                NavigationItem(AppScreen.CUSTOMERS, "Customers", Icons.Default.People),
                NavigationItem(AppScreen.READINGS, "Readings", Icons.Default.Speed),
                NavigationItem(AppScreen.BILLS, "Bills", Icons.Default.Receipt),
                NavigationItem(AppScreen.TARIFFS, "Tariffs", Icons.Default.Tune),
                NavigationItem(AppScreen.REPORTS, "Reports", Icons.Default.Assessment)
            )
            UserRole.DEVELOPER.name -> listOf(
                NavigationItem(AppScreen.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
                NavigationItem(AppScreen.CUSTOMERS, "Customers", Icons.Default.People),
                NavigationItem(AppScreen.READINGS, "Readings", Icons.Default.Speed),
                NavigationItem(AppScreen.BILLS, "Bills", Icons.Default.Receipt),
                NavigationItem(AppScreen.PAYMENTS, "Payments", Icons.Default.Payment),
                NavigationItem(AppScreen.TARIFFS, "Tariffs", Icons.Default.Tune),
                NavigationItem(AppScreen.USERS, "Users", Icons.Default.ManageAccounts),
                NavigationItem(AppScreen.DEVELOPER_SETTINGS, "Settings", Icons.Default.Settings),
                NavigationItem(AppScreen.REPORTS, "Reports", Icons.Default.Assessment)
            )
            else -> listOf(
                NavigationItem(AppScreen.DASHBOARD, "Dashboard", Icons.Default.Dashboard)
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            WaterTopAppBar(
                title = currentScreen.title,
                appName = appName,
                role = currentUser?.role,
                licenseInfo = licenseInfo,
                onLogoutClick = { viewModel.logout() }
            )
        },
        bottomBar = {
            if (navigationItems.size > 1) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    navigationItems.forEach { item ->
                        val isSelected = currentScreen == item.screen
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.navigateTo(item.screen) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = WaterBluePrimary,
                                indicatorColor = WaterBluePrimary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_${item.screen.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                AppScreen.DEVELOPER_SETUP -> DeveloperSetupScreen(viewModel = viewModel)
                AppScreen.LOGIN -> LoginScreen(viewModel = viewModel)
                AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                AppScreen.CUSTOMERS -> CustomerManagementScreen(viewModel = viewModel)
                AppScreen.READINGS -> ReadingScreen(viewModel = viewModel)
                AppScreen.BILLS -> BillsScreen(viewModel = viewModel)
                AppScreen.PAYMENTS -> PaymentScreen(viewModel = viewModel)
                AppScreen.TARIFFS -> TariffManagementScreen(viewModel = viewModel)
                AppScreen.REPORTS -> ReportsScreen(viewModel = viewModel)
                AppScreen.USERS -> UserManagementScreen(viewModel = viewModel)
                AppScreen.DEVELOPER_SETTINGS -> DeveloperSettingsScreen(viewModel = viewModel)
            }
        }
    }
}
