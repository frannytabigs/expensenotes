package com.example.expensenotes.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensenotes.model.NewExpenseViewModel
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(
    viewModel: NewExpenseViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. Setup SharedPreferences to save the input fields permanently
    val sharedPrefs = remember {
        context.getSharedPreferences("BackupPreferences", Context.MODE_PRIVATE)
    }

    var botToken by remember { mutableStateOf(sharedPrefs.getString("bot_token", "") ?: "") }
    var chatId by remember { mutableStateOf(sharedPrefs.getString("chat_id", "") ?: "") }

    // 2. Setup the Snackbar for pop-up messages
    val snackbarHostState = remember { SnackbarHostState() }

    // 3. Loading states to prevent spam-clicking
    var isUploading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var pendingJsonToSave by remember { mutableStateOf("") }

    // Launcher for SAVING the file to the device (Export)
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingJsonToSave.toByteArray())
                }
                scope.launch { snackbarHostState.showSnackbar("Backup saved to device successfully! ✅") }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Failed to save file to device.") }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Save cancelled.") }
        }
        isDownloading = false
    }

    // Launcher for OPENING the file from the device (Import/Restore)
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Read the file content
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonString = inputStream?.bufferedReader().use { it?.readText() }

                if (!jsonString.isNullOrBlank()) {
                    isRestoring = true
                    // Send the read string to the ViewModel to process
                    viewModel.restoreBackupFromJson(jsonString) { success, message ->
                        isRestoring = false
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("The selected file is empty or invalid.") }
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Error reading the file.") }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Restore cancelled.") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Import & Export Expenses",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // --- 1. TELEGRAM BACKUP CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Send to Telegram",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enter your bot details to securely send your expenses.json file to your chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = botToken,
                        onValueChange = {
                            botToken = it
                            sharedPrefs.edit().putString("bot_token", it).apply()
                        },
                        label = { Text("Bot Token") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUploading && !isRestoring
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = chatId,
                        onValueChange = {
                            chatId = it
                            sharedPrefs.edit().putString("chat_id", it).apply()
                        },
                        label = { Text("Chat ID") },
                        leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUploading && !isRestoring
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (botToken.isBlank() || chatId.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Please enter both Bot Token and Chat ID.") }
                                return@Button
                            }

                            isUploading = true

                            viewModel.generateBackupJson { jsonString ->
                                if (jsonString != null) {
                                    viewModel.sendBackupToTelegram(
                                        botToken = botToken,
                                        chatId = chatId,
                                        jsonContent = jsonString,
                                        onStatusMessage = { message ->
                                            if (message == "Sending...") {
                                                scope.launch { snackbarHostState.showSnackbar(message) }
                                            } else {
                                                isUploading = false
                                                scope.launch { snackbarHostState.showSnackbar(message) }
                                            }
                                        }
                                    )
                                } else {
                                    isUploading = false
                                    scope.launch { snackbarHostState.showSnackbar("Error generating JSON. Database might be empty.") }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isUploading && !isRestoring
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sending...")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Backup")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 2. LOCAL DEVICE BACKUP CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Save to Device",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Download your expenses as a JSON file directly to your phone's storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    FilledTonalButton(
                        onClick = {
                            isDownloading = true
                            viewModel.generateBackupJson { jsonString ->
                                if (jsonString != null) {
                                    pendingJsonToSave = jsonString
                                    saveFileLauncher.launch("expenses_backup.json")
                                } else {
                                    isDownloading = false
                                    scope.launch { snackbarHostState.showSnackbar("Error generating JSON.") }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading && !isRestoring
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preparing file...")
                        } else {
                            Icon(Icons.Default.SaveAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download expenses.json")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 3. RESTORE BACKUP CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Restore Data",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Warning: Restoring from a backup file will completely REPLACE your current expenses. This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            // Launch the file picker allowing the user to select only JSON files
                            openFileLauncher.launch(arrayOf("application/json"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        enabled = !isUploading && !isDownloading && !isRestoring
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restoring...")
                        } else {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore from File")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}