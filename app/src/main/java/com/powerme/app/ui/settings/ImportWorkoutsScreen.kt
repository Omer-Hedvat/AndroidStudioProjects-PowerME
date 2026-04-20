package com.powerme.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.data.csvimport.ColumnMapping
import com.powerme.app.data.csvimport.CsvFormat
import com.powerme.app.data.csvimport.DuplicateHandling
import com.powerme.app.data.csvimport.ImportOptions
import com.powerme.app.data.csvimport.ImportProgress
import com.powerme.app.data.csvimport.ImportResult
import com.powerme.app.data.csvimport.WeightUnit
import com.powerme.app.data.csvimport.DetectionResult
import com.powerme.app.ui.theme.PowerMeDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWorkoutsScreen(
    viewModel: ImportViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val step = uiState.step

    // Summary sheet visibility is driven by step == Complete
    val showSummary = step is ImportStep.Complete

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Workout History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            when (val s = step) {
                is ImportStep.Idle, is ImportStep.Loading -> {
                    item {
                        ImportCard(title = "Select CSV File") {
                            Text(
                                "Import your workout history from Strong, Hevy, FitBod, Jefit, or any CSV file.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { fileLauncher.launch(arrayOf("text/*")) },
                                enabled = step !is ImportStep.Loading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (step is ImportStep.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        Icons.Default.FileOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(if (step is ImportStep.Loading) "Reading file…" else "Choose CSV File")
                            }
                        }
                    }
                }

                is ImportStep.FormatDetected -> {
                    item {
                        FormatDetectedCard(
                            detection = s.detection,
                            workoutCount = s.rowCount,
                            previewRows = s.previewRows,
                            onImport = { viewModel.onConfirmFormat() },
                            onCancel = { viewModel.onDismiss() }
                        )
                    }
                }

                is ImportStep.ColumnMapping -> {
                    item {
                        ColumnMappingCard(
                            detection = s.detection,
                            previewRows = s.previewRows,
                            currentMapping = s.mapping,
                            onConfirm = { mapping -> viewModel.onColumnMappingComplete(mapping) },
                            onCancel = { viewModel.onDismiss() }
                        )
                    }
                }

                is ImportStep.Options -> {
                    item {
                        ImportOptionsCard(
                            detection = s.detection,
                            rowCount = s.rows.size,
                            options = s.options,
                            onStartImport = { opts -> viewModel.onStartImport(opts) },
                            onCancel = { viewModel.onDismiss() }
                        )
                    }
                }

                is ImportStep.Importing -> {
                    item { ImportingCard(progress = s.progress) }
                }

                is ImportStep.Complete -> {
                    // Sheet shown below via ModalBottomSheet
                }

                is ImportStep.Error -> {
                    item {
                        ImportCard(title = "Import Error") {
                            Text(
                                s.message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.onDismiss() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Try Again") }
                        }
                    }
                }
            }
        }

        if (showSummary) {
            val result = (step as ImportStep.Complete).result
            ImportSummarySheet(
                result = result,
                onUndo = { viewModel.onUndoImport(); onNavigateBack() },
                onDone = { viewModel.onDismiss(); onNavigateBack() }
            )
        }
    }
}

// ── Format Detected ───────────────────────────────────────────────────────────

@Composable
private fun FormatDetectedCard(
    detection: DetectionResult,
    workoutCount: Int,
    previewRows: List<List<String>>,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    ImportCard(title = "Format Detected") {
        val formatLabel = when (detection.format) {
            CsvFormat.STRONG -> "Strong"
            CsvFormat.HEVY -> "Hevy"
            CsvFormat.FITBOD -> "FitBod"
            CsvFormat.JEFIT -> "Jefit"
            CsvFormat.GENERIC -> "Generic CSV"
        }
        Text(
            "$formatLabel export detected — $workoutCount workouts found.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (previewRows.isNotEmpty()) {
            Text("Preview (first 3 rows):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            PreviewTable(headers = detection.headers, rows = previewRows)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Continue") }
        }
    }
}

// ── Column Mapping ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMappingCard(
    detection: DetectionResult,
    previewRows: List<List<String>>,
    currentMapping: ColumnMapping?,
    onConfirm: (ColumnMapping) -> Unit,
    onCancel: () -> Unit
) {
    val headers = detection.headers
    val none = "<none>"
    val options = listOf(none) + headers

    var dateCol by remember { mutableStateOf(currentMapping?.dateColumn?.let { headers.getOrNull(it) } ?: none) }
    var exerciseCol by remember { mutableStateOf(currentMapping?.exerciseColumn?.let { headers.getOrNull(it) } ?: none) }
    var repsCol by remember { mutableStateOf(currentMapping?.repsColumn?.let { headers.getOrNull(it) } ?: none) }
    var weightCol by remember { mutableStateOf(currentMapping?.weightColumn?.let { headers.getOrNull(it) } ?: none) }
    var workoutNameCol by remember { mutableStateOf(currentMapping?.workoutNameColumn?.let { headers.getOrNull(it) } ?: none) }
    var rpeCol by remember { mutableStateOf(currentMapping?.rpeColumn?.let { headers.getOrNull(it) } ?: none) }
    var notesCol by remember { mutableStateOf(currentMapping?.notesColumn?.let { headers.getOrNull(it) } ?: none) }

    val canConfirm = dateCol != none && exerciseCol != none && repsCol != none

    ImportCard(title = "Map Columns") {
        Text("Assign your CSV columns to PowerME fields.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        if (previewRows.isNotEmpty()) {
            Text("Preview:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            PreviewTable(headers = headers, rows = previewRows)
            Spacer(modifier = Modifier.height(16.dp))
        }

        ColumnDropdown("Date *", options, dateCol) { dateCol = it }
        ColumnDropdown("Exercise Name *", options, exerciseCol) { exerciseCol = it }
        ColumnDropdown("Reps *", options, repsCol) { repsCol = it }
        ColumnDropdown("Weight (optional)", options, weightCol) { weightCol = it }
        ColumnDropdown("Workout Name (optional)", options, workoutNameCol) { workoutNameCol = it }
        ColumnDropdown("RPE (optional)", options, rpeCol) { rpeCol = it }
        ColumnDropdown("Notes (optional)", options, notesCol) { notesCol = it }

        Text("* Required", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    onConfirm(
                        ColumnMapping(
                            dateColumn = headers.indexOf(dateCol),
                            exerciseColumn = headers.indexOf(exerciseCol),
                            repsColumn = headers.indexOf(repsCol),
                            weightColumn = weightCol.takeIf { it != none }?.let { headers.indexOf(it) },
                            workoutNameColumn = workoutNameCol.takeIf { it != none }?.let { headers.indexOf(it) },
                            rpeColumn = rpeCol.takeIf { it != none }?.let { headers.indexOf(it) },
                            notesColumn = notesCol.takeIf { it != none }?.let { headers.indexOf(it) }
                        )
                    )
                },
                enabled = canConfirm,
                modifier = Modifier.weight(1f)
            ) { Text("Continue") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            label = { Text(label, fontSize = 12.sp) },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 13.sp) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

// ── Import Options ────────────────────────────────────────────────────────────

@Composable
private fun ImportOptionsCard(
    detection: DetectionResult,
    rowCount: Int,
    options: ImportOptions,
    onStartImport: (ImportOptions) -> Unit,
    onCancel: () -> Unit
) {
    var duplicateHandling by remember { mutableStateOf(options.duplicateHandling) }
    val defaultWeightUnit = when (detection.format) {
        CsvFormat.FITBOD -> WeightUnit.LBS
        else -> WeightUnit.KG
    }
    var weightUnit by remember { mutableStateOf(defaultWeightUnit) }
    var createNew by remember { mutableStateOf(options.createNewExercises) }

    ImportCard(title = "Import Options") {
        Text(
            "$rowCount set rows ready to import.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Duplicates", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = duplicateHandling == DuplicateHandling.SKIP,
                onClick = { duplicateHandling = DuplicateHandling.SKIP },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Skip", fontSize = 12.sp) }
            SegmentedButton(
                selected = duplicateHandling == DuplicateHandling.IMPORT_ANYWAY,
                onClick = { duplicateHandling = DuplicateHandling.IMPORT_ANYWAY },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Import All", fontSize = 12.sp) }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("Weight Unit", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = weightUnit == WeightUnit.KG,
                onClick = { weightUnit = WeightUnit.KG },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("kg", fontSize = 12.sp) }
            SegmentedButton(
                selected = weightUnit == WeightUnit.LBS,
                onClick = { weightUnit = WeightUnit.LBS },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("lbs → kg", fontSize = 12.sp) }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Create new exercises", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Exercises not in your library will be added automatically.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = createNew,
                onCheckedChange = { createNew = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    onStartImport(
                        options.copy(
                            weightUnit = weightUnit,
                            duplicateHandling = duplicateHandling,
                            createNewExercises = createNew
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Start Import") }
        }
    }
}

// ── Importing Progress ────────────────────────────────────────────────────────

@Composable
private fun ImportingCard(progress: ImportProgress) {
    ImportCard(title = "Importing…") {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Importing workout ${progress.processedWorkouts} of ${progress.totalWorkouts}…",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (progress.exercisesCreated > 0) {
            Text(
                "${progress.exercisesCreated} new exercises added to library",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Summary Sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSummarySheet(
    result: ImportResult,
    onUndo: () -> Unit,
    onDone: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showErrors by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Import Complete", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))

            SummaryRow(label = "Workouts imported", value = result.workoutsImported.toString())
            SummaryRow(label = "Sets imported", value = result.setsImported.toString())
            if (result.exercisesCreated > 0) {
                SummaryRow(label = "New exercises added", value = result.exercisesCreated.toString())
            }
            if (result.rowsSkipped > 0) {
                SummaryRow(label = "Rows skipped", value = result.rowsSkipped.toString())
            }
            if (result.errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showErrors = !showErrors }) {
                    Text(
                        if (showErrors) "Hide errors (${result.errors.size})"
                        else "Show errors (${result.errors.size})",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (showErrors) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        result.errors.forEach { err ->
                            Text(
                                "• $err",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Undo Import") }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun ImportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun PreviewTable(headers: List<String>, rows: List<List<String>>) {
    val scrollState = rememberScrollState()
    val cellWidth = 100.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp)
    ) {
        // Header row
        Row {
            headers.take(8).forEach { h ->
                Text(
                    h,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(cellWidth).padding(horizontal = 4.dp),
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        // Data rows
        rows.forEach { row ->
            Row {
                row.take(8).forEach { cell ->
                    Text(
                        cell,
                        fontSize = 10.sp,
                        modifier = Modifier.width(cellWidth).padding(horizontal = 4.dp),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
