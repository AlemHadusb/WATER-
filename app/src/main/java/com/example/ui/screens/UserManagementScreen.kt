package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.ui.components.RoleBadge
import com.example.ui.theme.WaterBluePrimary
import com.example.ui.theme.WaterGreenPaid
import com.example.ui.theme.WaterRedExpired
import com.example.viewmodel.WaterViewModel

@Composable
fun UserManagementScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier
) {
    val users by viewModel.allUsers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var userToResetPassword by remember { mutableStateOf<User?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "System User Management",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Developer Role Access Only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("create_user_button")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add User")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { it.id }) { user ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = user.username,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ID: #${user.id} • Created: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(user.createdAt))}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RoleBadge(role = user.role)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { userToResetPassword = user }) {
                                Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Password", fontSize = 12.sp)
                            }

                            if (user.role != UserRole.DEVELOPER.name) {
                                TextButton(
                                    onClick = { viewModel.toggleUserStatus(user.id, !user.active) },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (user.active) WaterRedExpired else WaterGreenPaid
                                    )
                                ) {
                                    Icon(
                                        if (user.active) Icons.Default.Block else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (user.active) "Deactivate" else "Activate", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create User Dialog
    if (showCreateDialog) {
        CreateUserDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { username, password, role ->
                viewModel.createNewUser(username, password, role) {
                    showCreateDialog = false
                }
            }
        )
    }

    // Reset Password Dialog
    if (userToResetPassword != null) {
        ResetPasswordDialog(
            user = userToResetPassword!!,
            onDismiss = { userToResetPassword = null },
            onConfirm = { newPass ->
                viewModel.resetUserPassword(userToResetPassword!!.id, newPass) {
                    userToResetPassword = null
                }
            }
        )
    }
}

@Composable
fun CreateUserDialog(
    onDismiss: () -> Unit,
    onCreate: (username: String, password: String, role: UserRole) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.ACCOUNTANT) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val roles = listOf(UserRole.ADMIN, UserRole.ACCOUNTANT, UserRole.READING_USER)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Create New System User",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WaterBluePrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (validationError != null) {
                    Text(text = validationError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; validationError = null },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_user_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; validationError = null },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_user_password_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Assign Role", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                roles.forEach { role ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role }
                        )
                        Text(text = role.displayName, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (username.isBlank() || password.length < 4) {
                                validationError = "Username required & password must be at least 4 chars."
                                return@Button
                            }
                            onCreate(username, password, selectedRole)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary),
                        modifier = Modifier.testTag("submit_create_user_button")
                    ) {
                        Text("Create User")
                    }
                }
            }
        }
    }
}

@Composable
fun ResetPasswordDialog(
    user: User,
    onDismiss: () -> Unit,
    onConfirm: (newPass: String) -> Unit
) {
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Reset Password for '${user.username}'",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WaterBluePrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (errorMsg != null) {
                    Text(text = errorMsg ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it; errorMsg = null },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it; errorMsg = null },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newPass.length < 4) {
                                errorMsg = "Password must be at least 4 characters."
                                return@Button
                            }
                            if (newPass != confirmPass) {
                                errorMsg = "Passwords do not match."
                                return@Button
                            }
                            onConfirm(newPass)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBluePrimary)
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}
