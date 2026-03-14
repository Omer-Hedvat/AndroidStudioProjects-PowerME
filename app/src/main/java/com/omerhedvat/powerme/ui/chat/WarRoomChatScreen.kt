package com.omerhedvat.powerme.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.actions.ActionResult
import com.omerhedvat.powerme.data.database.ChatMessage
import com.omerhedvat.powerme.util.MedicalPatch
import kotlinx.coroutines.launch

@Composable
fun WarRoomChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
    onDismissApiKeyDialog: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val medicalDoc by viewModel.medicalDoc.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var showMedicalSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Medical patch confirmation dialog
    uiState.pendingMedicalPatch?.let { patch ->
        MedicalPatchConfirmationDialog(
            patch = patch,
            onConfirm = { viewModel.confirmMedicalPatch() },
            onReject = { viewModel.rejectMedicalPatch() }
        )
    }

    // API key required dialog
    if (uiState.needsApiKey) {
        AlertDialog(
            onDismissRequest = { onDismissApiKeyDialog() },
            title = {
                Text("API Key Required", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Please configure your Gemini API Key in Settings to use the War Room chat feature.",
                    color = MaterialTheme.colorScheme.primary
                )
            },
            confirmButton = {
                Button(
                    onClick = onNavigateToSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("Go to Settings", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            WarRoomHeader(
                onClear = { viewModel.clearChat() },
                onShieldTap = { showMedicalSheet = true }
            )

            // Medical Shield bottom sheet
            if (showMedicalSheet) {
                MedicalShieldBottomSheet(
                    medicalDoc = medicalDoc,
                    onDismiss = { showMedicalSheet = false },
                    onUpdateViaChat = {
                        messageText = "אני רוצה לעדכן את המגבלות הרפואיות שלי"
                        showMedicalSheet = false
                    }
                )
            }

            // Error message
            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Session reset banner
            if (uiState.sessionWasReset) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(4000)
                    viewModel.dismissSessionReset()
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✦ New session — previous context saved to memory",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissSessionReset() }) {
                            Text("OK", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Mode-driven content
            when (uiState.warRoomMode) {
                WarRoomMode.LOADING -> FullScreenLoadingSpinner()
                WarRoomMode.INTERVIEW -> InterviewModeUI(uiState, viewModel, listState, messageText, { messageText = it })
                WarRoomMode.ACTIVE -> ActiveChatUI(uiState, viewModel, listState, messageText, { messageText = it })
            }
        }
    }
}

@Composable
fun WarRoomHeader(onClear: () -> Unit, onShieldTap: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WAR ROOM",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "הוועדה — 8 יועצים מומחים",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShieldTap) {
                    Icon(Icons.Default.Shield, contentDescription = "Medical Shield", tint = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onClear) {
                    Text("Clear", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun FullScreenLoadingSpinner() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "מאתחל את הוועדה...",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun InterviewModeUI(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    messageText: String,
    onMessageChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Messages list (shows interview Q&A)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                item {
                    InterviewSetupCard(onStart = { viewModel.startDiscoveryInterview() })
                }
            }
            items(uiState.messages) { message ->
                MessageBubble(message = message)
            }
            if (uiState.isLoading) {
                item { LoadingIndicator() }
            }
        }

        // Input area (shown during interview to collect answers)
        if (uiState.messages.isNotEmpty()) {
            ChatInputArea(
                messageText = messageText,
                onMessageChange = onMessageChange,
                isLoading = uiState.isLoading,
                onSend = {
                    viewModel.sendMessage(messageText)
                    onMessageChange("")
                }
            )
        }
    }
}

@Composable
fun InterviewSetupCard(onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✦ FIRST TIME SETUP",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Dr. Brad & Noa need to define your training phase before we begin.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Text("Start Interview", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ActiveChatUI(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    messageText: String,
    onMessageChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                item { WelcomeMessage() }
            }

            items(uiState.messages) { message ->
                MessageBubble(message = message)
            }

            if (uiState.actionResults.isNotEmpty()) {
                items(uiState.actionResults) { actionResult ->
                    ActionBadge(
                        actionResult = actionResult,
                        onDismiss = { viewModel.clearActionResults() }
                    )
                }
            }

            if (uiState.isLoading) {
                item { LoadingIndicator() }
            }
        }

        // Routine preview card (shown after create_workout_routine action succeeds)
        uiState.pendingRoutine?.let { routine ->
            RoutinePreviewCard(
                routine = routine,
                onSave = { viewModel.dismissRoutinePreview() },
                onDismiss = { viewModel.deleteRoutineAndClear(routine.routineId) }
            )
        }

        ChatInputArea(
            messageText = messageText,
            onMessageChange = onMessageChange,
            isLoading = uiState.isLoading,
            onSend = {
                viewModel.sendMessage(messageText)
                onMessageChange("")
            }
        )
    }
}

@Composable
fun ChatInputArea(
    messageText: String,
    onMessageChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Ask the Committee...", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    focusedTextColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !isLoading,
                maxLines = 4
            )
            IconButton(
                onClick = { if (messageText.isNotBlank()) onSend() },
                enabled = messageText.isNotBlank() && !isLoading
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (messageText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun MedicalPatchConfirmationDialog(
    patch: MedicalPatch,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text("⚠ Noa proposes a Medical Update", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "REASON: \"${patch.reason}\"",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
                if (patch.redListAdd.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ADD to RED LIST:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    patch.redListAdd.forEach {
                        Text("🔴 $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                    }
                }
                if (patch.yellowListAdd.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ADD to YELLOW LIST:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    patch.yellowListAdd.forEach {
                        Text(
                            "🟡 ${it.exercise} → \"${it.requiredCue}\"",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Reject", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun WelcomeMessage() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "ברוך הבא לחדר המלחמה",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "אתה מדבר עם הוועדה — 8 יועצים מומחים:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                "Arnold — גוף-מוח, פאמפ",
                "Dr. Brad Schoenfeld — היפרטרופיה מדעית",
                "Noa — פיזיותרפיסטית, מניעת פציעות",
                "Boris Sheiko — כוח והתקדמות",
                "Coach Carter — התאוששות",
                "Maya — תזונה",
                "Boaz — מדעני נתונים",
                "The Architect — מנהל הוועדה"
            ).forEach { expert ->
                Text(
                    text = "• $expert",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (message.isUser) 12.dp else 0.dp,
                bottomEnd = if (message.isUser) 0.dp else 12.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isUser) {
                    Text(
                        text = "הוועדה",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = message.message,
                    fontSize = 14.sp,
                    color = if (message.isUser) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 200.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "הוועדה מתייעצת...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RoutinePreviewCard(
    routine: PendingRoutinePreview,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "📋 Routine Ready: \"${routine.routineName}\"",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${routine.exercises.size} exercises · Target: ${routine.targetDate}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            routine.exercises.forEachIndexed { index, ex ->
                Text(
                    text = "${index + 1}. ${ex.exerciseName}  ${ex.targetSets}×${ex.targetRepsMin}-${ex.targetRepsMax} @RPE${ex.targetRpe} · ${ex.restSeconds}s rest",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("Save to Tracker", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Dismiss", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalShieldBottomSheet(
    medicalDoc: MedicalRestrictionsDoc?,
    onDismiss: () -> Unit,
    onUpdateViaChat: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "🛡 Medical Shield — Noa",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (medicalDoc == null) {
                Text(
                    text = "No restrictions configured yet.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            } else {
                // RED LIST
                Text(
                    text = "RED LIST — Forbidden Movements",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (medicalDoc.redList.isEmpty()) {
                    Text("(none)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                } else {
                    medicalDoc.redList.forEach { item ->
                        Text(
                            text = "🔴 $item",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // YELLOW LIST
                Text(
                    text = "YELLOW LIST — Modify With Cue",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (medicalDoc.yellowList.isEmpty()) {
                    Text("(none)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                } else {
                    medicalDoc.yellowList.forEach { entry ->
                        Text(
                            text = "🟡 ${entry.exercise} → ${entry.requiredCue}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onUpdateViaChat,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Text("Update via Chat", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ActionBadge(actionResult: ActionResult, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (actionResult) {
                is ActionResult.Success -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                is ActionResult.Failure -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (actionResult) {
                        is ActionResult.Success -> Icons.Default.CheckCircle
                        is ActionResult.Failure -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when (actionResult) {
                        is ActionResult.Success -> "✓ ${actionResult.message}"
                        is ActionResult.Failure -> "✗ ${actionResult.error}"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
