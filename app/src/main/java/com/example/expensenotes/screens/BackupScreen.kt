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
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
    val uriHandler = LocalUriHandler.current

    // 1. Setup SharedPreferences to save the input fields permanently
    val sharedPrefs = remember {
        context.getSharedPreferences("BackupPreferences", Context.MODE_PRIVATE)
    }

    var botToken by remember { mutableStateOf(sharedPrefs.getString("bot_token", "") ?: "") }
    var chatId by remember { mutableStateOf(sharedPrefs.getString("chat_id", "") ?: "") }

    // 2. Setup UI Control States
    val snackbarHostState = remember { SnackbarHostState() }
    var showHelpDialog by remember { mutableStateOf(false) }

    // 3. Loading states
    var isUploading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var pendingJsonToSave by remember { mutableStateOf("") }

    // THE MAGIC VARIABLE: This checks if ANY popup message is currently visible on the screen
    val isMessageShowing = snackbarHostState.currentSnackbarData != null

    // Disables all buttons if the app is uploading, downloading, restoring, OR if a message is showing
    val isBusy = isUploading || isDownloading || isRestoring || isMessageShowing

    // Launcher for SAVING the file to the device (Export)
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingJsonToSave.toByteArray())
                }
                scope.launch { snackbarHostState.showSnackbar("Backup saved to device successfully! ✅", duration = SnackbarDuration.Long) }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Failed to save file to device.", duration = SnackbarDuration.Long) }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Save cancelled.", duration = SnackbarDuration.Short) }
        }
        isDownloading = false
    }

    // Launcher for OPENING the file from the device (Import/Restore)
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonString = inputStream?.bufferedReader().use { it?.readText() }

                if (!jsonString.isNullOrBlank()) {
                    isRestoring = true
                    viewModel.restoreBackupFromJson(jsonString) { success, message ->
                        isRestoring = false
                        scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long) }
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("The selected file is empty or invalid.", duration = SnackbarDuration.Long) }
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Error reading the file.", duration = SnackbarDuration.Long) }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Restore cancelled.", duration = SnackbarDuration.Short) }
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
                text = "Backup & Export",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Send to Telegram",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = { showHelpDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Help Guide",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

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
                        enabled = !isBusy // Disables if busy or message showing
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
                        enabled = !isBusy // Disables if busy or message showing
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (botToken.isBlank() || chatId.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Please enter both Bot Token and Chat ID.", duration = SnackbarDuration.Short) }
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
                                            // We skip showing the "Sending..." snackbar to avoid spam,
                                            // because the button already has a loading spinner!
                                            if (message != "Sending...") {
                                                isUploading = false
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    isUploading = false
                                    scope.launch { snackbarHostState.showSnackbar("Error generating JSON. Database might be empty.", duration = SnackbarDuration.Long) }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy // Prevents spam clicking
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
                                    scope.launch { snackbarHostState.showSnackbar("Error generating JSON.", duration = SnackbarDuration.Long) }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
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
                            openFileLauncher.launch(arrayOf("application/json"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        enabled = !isBusy
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

    // --- FANCY TELEGRAM HELP POP-UP DIALOG ---
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "Telegram Setup Guide 🤖",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "To send backups directly to your chat, you will need a Bot Token and your Chat ID.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "🔑 Bot Token:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Message @BotFather on Telegram, use the /newbot command, and copy the long HTTP API token provided.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "💬 Chat ID:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Message @userinfobot or @RawDataBot on Telegram to get your unique account ID number.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "💡 Vital Note: You MUST open a conversation with your bot inside Telegram and click \"Start\" before attempting to send a backup from this app!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        uriHandler.openUri("https://core.telegram.org/bots/features#creating-a-new-bot")
                    }
                ) {
                    Text("Read Docs Website 🌐")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}