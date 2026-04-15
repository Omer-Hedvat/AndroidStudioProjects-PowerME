package com.powerme.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.ExperienceLevel
import com.powerme.app.data.database.HealthHistoryEntry
import com.powerme.app.data.database.HealthHistorySeverity
import com.powerme.app.data.database.HealthHistoryType
import com.powerme.app.ui.components.MultiSelectChips
import com.powerme.app.ui.components.ProfileTextField
import com.powerme.app.ui.components.SingleChoiceSegmented
import com.powerme.app.ui.components.TRAINING_TARGET_OPTIONS
import com.powerme.app.ui.components.rememberSelectAllState
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.util.UnitConverter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val userEmail = Firebase.auth.currentUser?.email ?: ""

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Profile Header ─────────────────────────────────────
            item {
                ProfileHeader(name = uiState.nameInput, email = userEmail)
            }

            // ── Personal Info ──────────────────────────────────────
            item {
                PersonalInfoCard(uiState = uiState, viewModel = viewModel)
            }

            // ── Body Metrics ───────────────────────────────────────
            item {
                BodyMetricsCard(uiState = uiState, viewModel = viewModel)
            }

            // ── Fitness Level ──────────────────────────────────────
            item {
                FitnessLevelCard(uiState = uiState, viewModel = viewModel)
            }

            // ── Health History ─────────────────────────────────────
            item {
                HealthHistoryCard(
                    entries = uiState.healthHistoryEntries,
                    onAddClick = viewModel::openAddHealthEntry,
                    onEntryClick = viewModel::openEditHealthEntry
                )
            }
        }
    }

    // ── Health History Bottom Sheet ─────────────────────────────
    if (uiState.showHealthHistorySheet) {
        HealthHistoryBottomSheet(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = viewModel::dismissHealthHistorySheet
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(name: String, email: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name.ifBlank { "Your Profile" },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = email,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Personal Info Card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalInfoCard(uiState: ProfileUiState, viewModel: ProfileViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }

    val dobDisplay = uiState.dateOfBirth?.let { epochMs ->
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } ?: ""

    if (showDatePicker) {
        val today = java.time.LocalDate.now()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.dateOfBirth,
            yearRange = 1920..(today.year - 5)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.updateDateOfBirth(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ProfileCard(title = "Personal Info") {
        ProfileTextField(
            value = uiState.nameInput,
            onValueChange = viewModel::updateNameInput,
            label = "Name",
            imeAction = ImeAction.Next,
            onImeAction = {}
        )
        Spacer(modifier = Modifier.height(12.dp))

        val dobInteractionSource = remember { MutableInteractionSource() }
        val dobPressed by dobInteractionSource.collectIsPressedAsState()
        LaunchedEffect(dobPressed) { if (dobPressed) showDatePicker = true }
        OutlinedTextField(
            value = dobDisplay,
            onValueChange = {},
            label = { Text("Date of Birth", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            interactionSource = dobInteractionSource,
            trailingIcon = {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "Pick date",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            },
            colors = PowerMeDefaults.outlinedTextFieldColors()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileTextField(
                    value = uiState.averageSleepHoursInput,
                    onValueChange = viewModel::updateSleepHoursInput,
                    label = "Avg Sleep (h)",
                    imeAction = ImeAction.Next,
                    onImeAction = {},
                    keyboardType = KeyboardType.Decimal
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileTextField(
                    value = uiState.parentalLoadInput,
                    onValueChange = viewModel::updateParentalLoadInput,
                    label = "Children",
                    imeAction = ImeAction.Done,
                    onImeAction = {},
                    keyboardType = KeyboardType.Number
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Gender", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmented(
            options = listOf("MALE", "FEMALE", "OTHER"),
            selected = uiState.gender,
            onSelect = viewModel::updateGender
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Occupation", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmented(
            options = listOf("SEDENTARY", "ACTIVE", "PHYSICAL"),
            selected = uiState.occupationType,
            onSelect = viewModel::updateOccupationType
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Chronotype", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmented(
            options = listOf("MORNING", "NEUTRAL", "NIGHT"),
            selected = uiState.chronotype,
            onSelect = viewModel::updateChronotype
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Training Goals", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        MultiSelectChips(
            options = TRAINING_TARGET_OPTIONS,
            selected = uiState.selectedTrainingTargets,
            onToggle = viewModel::toggleTrainingTarget
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (uiState.isSavingPersonalInfo) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            Button(
                onClick = viewModel::savePersonalInfo,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                val msg = uiState.personalInfoSaveMessage
                if (msg != null) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TimerGreen
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(msg, fontWeight = FontWeight.Bold)
                } else {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }
            LaunchedEffect(uiState.personalInfoSaveMessage) {
                if (uiState.personalInfoSaveMessage != null) {
                    kotlinx.coroutines.delay(2000)
                    viewModel.dismissPersonalInfoSaveMessage()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Body Metrics Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BodyMetricsCard(uiState: ProfileUiState, viewModel: ProfileViewModel) {
    ProfileCard(title = "Body Metrics") {
        val bodyFatFocusRequester = remember { FocusRequester() }
        val heightFocusRequester = remember { FocusRequester() }
        val unit = uiState.unitSystem

        val lastText = buildString {
            val w = uiState.lastWeight
            val bf = uiState.lastBodyFat
            val h = uiState.lastHeight
            if (w != null || bf != null || h != null) {
                append("Last: ")
                if (w != null) append(UnitConverter.formatWeight(w, unit))
                if (w != null && bf != null) append(" / ")
                if (bf != null) append("${"%.1f".format(bf)}%")
                if ((w != null || bf != null) && h != null) append(" / ")
                if (h != null) append(UnitConverter.formatHeight(h.toDouble(), unit))
            }
        }
        if (lastText.isNotBlank()) {
            Text(lastText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val weightLabel = "Weight (${UnitConverter.weightLabel(unit)})"
            val (weightTfv, weightSelectMod) = rememberSelectAllState(uiState.weightInput)
            OutlinedTextField(
                value = weightTfv.value,
                onValueChange = { newTfv -> weightTfv.value = newTfv; viewModel.updateWeightInput(newTfv.text) },
                label = { Text(weightLabel, fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { bodyFatFocusRequester.requestFocus() }),
                modifier = Modifier.weight(1f).then(weightSelectMod),
                colors = PowerMeDefaults.outlinedTextFieldColors(),
                singleLine = true
            )
            val (bodyFatTfv, bodyFatSelectMod) = rememberSelectAllState(uiState.bodyFatInput)
            OutlinedTextField(
                value = bodyFatTfv.value,
                onValueChange = { newTfv -> bodyFatTfv.value = newTfv; viewModel.updateBodyFatInput(newTfv.text) },
                label = { Text("Body Fat (%)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { heightFocusRequester.requestFocus() }),
                modifier = Modifier.weight(1f).focusRequester(bodyFatFocusRequester).then(bodyFatSelectMod),
                colors = PowerMeDefaults.outlinedTextFieldColors(),
                singleLine = true
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (unit == UnitSystem.IMPERIAL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val (feetTfv, feetSelectMod) = rememberSelectAllState(uiState.heightFeetInput)
                OutlinedTextField(
                    value = feetTfv.value,
                    onValueChange = { newTfv -> feetTfv.value = newTfv; viewModel.updateHeightFeetInput(newTfv.text) },
                    label = { Text("Height (ft)", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { heightFocusRequester.requestFocus() }),
                    modifier = Modifier.weight(1f).then(feetSelectMod),
                    colors = PowerMeDefaults.outlinedTextFieldColors(),
                    singleLine = true
                )
                val (inchesTfv, inchesSelectMod) = rememberSelectAllState(uiState.heightInchesInput)
                OutlinedTextField(
                    value = inchesTfv.value,
                    onValueChange = { newTfv -> inchesTfv.value = newTfv; viewModel.updateHeightInchesInput(newTfv.text) },
                    label = { Text("Height (in)", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.saveBodyMetrics() }),
                    modifier = Modifier.weight(1f).focusRequester(heightFocusRequester).then(inchesSelectMod),
                    colors = PowerMeDefaults.outlinedTextFieldColors(),
                    singleLine = true
                )
            }
        } else {
            val (heightTfv, heightSelectMod) = rememberSelectAllState(uiState.heightInput)
            OutlinedTextField(
                value = heightTfv.value,
                onValueChange = { newTfv -> heightTfv.value = newTfv; viewModel.updateHeightInput(newTfv.text) },
                label = { Text("Height (cm)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.saveBodyMetrics() }),
                modifier = Modifier.fillMaxWidth().focusRequester(heightFocusRequester).then(heightSelectMod),
                colors = PowerMeDefaults.outlinedTextFieldColors(),
                singleLine = true
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isSavingMetrics) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Button(
                onClick = viewModel::saveBodyMetrics,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) { Text("Save") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fitness Level Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FitnessLevelCard(uiState: ProfileUiState, viewModel: ProfileViewModel) {
    ProfileCard(title = "Fitness Level") {
        val levels = ExperienceLevel.entries
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 2x2 grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                levels.take(2).forEach { level ->
                    FitnessLevelTile(
                        level = level,
                        selected = uiState.experienceLevel == level,
                        onClick = { viewModel.updateExperienceLevel(level) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                levels.drop(2).forEach { level ->
                    FitnessLevelTile(
                        level = level,
                        selected = uiState.experienceLevel == level,
                        onClick = { viewModel.updateExperienceLevel(level) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Training Age", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Text(
                text = if (uiState.trainingAgeYears == 0) "< 1 year"
                       else if (uiState.trainingAgeYears == 30) "30+ years"
                       else "${uiState.trainingAgeYears} yr",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = uiState.trainingAgeYears.toFloat(),
            onValueChange = { viewModel.updateTrainingAge(it.toInt()) },
            valueRange = 0f..30f,
            steps = 29,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun FitnessLevelTile(
    level: ExperienceLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                  else MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = level.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = level.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Health History Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HealthHistoryCard(
    entries: List<HealthHistoryEntry>,
    onAddClick: () -> Unit,
    onEntryClick: (HealthHistoryEntry) -> Unit
) {
    ProfileCard(title = "Health History") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Injuries, conditions & restrictions",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddClick) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add entry",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (entries.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No entries yet. Tap + to add an injury, surgery, or condition.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { entry ->
                    HealthHistoryEntryRow(entry = entry, onClick = { onEntryClick(entry) })
                }
            }
        }
    }
}

@Composable
private fun HealthHistoryEntryRow(entry: HealthHistoryEntry, onClick: () -> Unit) {
    val severity = runCatching { HealthHistorySeverity.valueOf(entry.severity) }
        .getOrElse { HealthHistorySeverity.MODERATE }
    val borderColor = when (severity) {
        HealthHistorySeverity.SEVERE -> MaterialTheme.colorScheme.error
        HealthHistorySeverity.MODERATE -> MaterialTheme.colorScheme.tertiary
        HealthHistorySeverity.MILD -> TimerGreen
        HealthHistorySeverity.RESOLVED -> MaterialTheme.colorScheme.outlineVariant
    }
    val type = runCatching { HealthHistoryType.valueOf(entry.type) }
        .getOrElse { HealthHistoryType.INJURY }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 0.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored severity bar on the left
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .background(borderColor, shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = entry.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = type.displayName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                entry.bodyRegion?.let { region ->
                    Text("·", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text(region, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Text("·", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text(severity.displayName, fontSize = 11.sp, color = borderColor)
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Edit",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Health History Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthHistoryBottomSheet(
    uiState: ProfileUiState,
    viewModel: ProfileViewModel,
    onDismiss: () -> Unit
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showResolvedDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (uiState.editingHealthEntry == null) "Add Health Entry" else "Edit Health Entry",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Type
            Text("Type", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HealthHistoryType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = uiState.sheetType == type,
                        onClick = { viewModel.updateSheetType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = HealthHistoryType.entries.size)
                    ) {
                        Text(type.displayName, fontSize = 11.sp)
                    }
                }
            }

            // Title
            OutlinedTextField(
                value = uiState.sheetTitle,
                onValueChange = viewModel::updateSheetTitle,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = PowerMeDefaults.outlinedTextFieldColors()
            )

            // Body Region
            OutlinedTextField(
                value = uiState.sheetBodyRegion,
                onValueChange = viewModel::updateSheetBodyRegion,
                label = { Text("Body Region (e.g. Lower Back, Knee)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = PowerMeDefaults.outlinedTextFieldColors()
            )

            // Severity
            Text("Severity", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HealthHistorySeverity.entries.forEachIndexed { index, severity ->
                    SegmentedButton(
                        selected = uiState.sheetSeverity == severity,
                        onClick = { viewModel.updateSheetSeverity(severity) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = HealthHistorySeverity.entries.size)
                    ) {
                        Text(severity.displayName, fontSize = 11.sp)
                    }
                }
            }

            // Start Date
            DatePickerField(
                label = "Start Date",
                epochMs = uiState.sheetStartDate,
                onDateSelected = viewModel::updateSheetStartDate,
                showPicker = showStartDatePicker,
                onShowPicker = { showStartDatePicker = true },
                onDismissPicker = { showStartDatePicker = false }
            )

            // Resolved Date (only when severity == RESOLVED)
            if (uiState.sheetSeverity == HealthHistorySeverity.RESOLVED) {
                DatePickerField(
                    label = "Resolved Date",
                    epochMs = uiState.sheetResolvedDate,
                    onDateSelected = viewModel::updateSheetResolvedDate,
                    showPicker = showResolvedDatePicker,
                    onShowPicker = { showResolvedDatePicker = true },
                    onDismissPicker = { showResolvedDatePicker = false }
                )
            }

            // Notes
            OutlinedTextField(
                value = uiState.sheetNotes,
                onValueChange = viewModel::updateSheetNotes,
                label = { Text("Notes / modification cue") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                colors = PowerMeDefaults.outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.editingHealthEntry != null) {
                    OutlinedButton(
                        onClick = { viewModel.archiveHealthEntry(uiState.editingHealthEntry.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Text("Delete")
                    }
                }
                Button(
                    onClick = viewModel::saveHealthEntry,
                    enabled = uiState.sheetTitle.isNotBlank(),
                    modifier = Modifier.weight(if (uiState.editingHealthEntry != null) 1f else 1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    epochMs: Long?,
    onDateSelected: (Long?) -> Unit,
    showPicker: Boolean,
    onShowPicker: () -> Unit,
    onDismissPicker: () -> Unit
) {
    val display = epochMs?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } ?: ""

    if (showPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = epochMs)
        DatePickerDialog(
            onDismissRequest = onDismissPicker,
            confirmButton = {
                TextButton(onClick = {
                    onDateSelected(datePickerState.selectedDateMillis)
                    onDismissPicker()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = onDismissPicker) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed) { if (pressed) onShowPicker() }

    OutlinedTextField(
        value = display,
        onValueChange = {},
        label = { Text(label, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        interactionSource = interactionSource,
        trailingIcon = {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = "Pick date",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        },
        colors = PowerMeDefaults.outlinedTextFieldColors()
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared card wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.subtleCardElevation()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
