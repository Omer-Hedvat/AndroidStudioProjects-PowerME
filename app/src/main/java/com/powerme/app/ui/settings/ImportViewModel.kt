package com.powerme.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.csvimport.ColumnMapping
import com.powerme.app.data.csvimport.CsvFormat
import com.powerme.app.data.csvimport.CsvFormatDetector
import com.powerme.app.data.csvimport.CsvImportManager
import com.powerme.app.data.csvimport.CsvRowParser
import com.powerme.app.data.csvimport.ImportOptions
import com.powerme.app.data.csvimport.ImportProgress
import com.powerme.app.data.csvimport.ImportResult
import com.powerme.app.data.csvimport.ParsedWorkoutRow
import com.powerme.app.data.csvimport.DetectionResult
import com.powerme.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ── State ─────────────────────────────────────────────────────────────────────

sealed class ImportStep {
    object Idle : ImportStep()
    object Loading : ImportStep()
    data class FormatDetected(
        val detection: DetectionResult,
        val rowCount: Int,
        val previewRows: List<List<String>>   // first 3 data rows as raw fields
    ) : ImportStep()
    data class ColumnMapping(
        val detection: DetectionResult,
        val previewRows: List<List<String>>,
        val mapping: com.powerme.app.data.csvimport.ColumnMapping? = null
    ) : ImportStep()
    data class Options(
        val detection: DetectionResult,
        val rows: List<ParsedWorkoutRow>,
        val options: ImportOptions = ImportOptions()
    ) : ImportStep()
    data class Importing(val progress: ImportProgress) : ImportStep()
    data class Complete(val result: ImportResult) : ImportStep()
    data class Error(val message: String) : ImportStep()
}

data class ImportUiState(val step: ImportStep = ImportStep.Idle)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val csvImportManager: CsvImportManager,
    private val workoutRepository: WorkoutRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** Cached parsed rows and last completed batch ID for undo. */
    private var cachedLines: List<String> = emptyList()
    private var cachedDetection: DetectionResult? = null
    private var cachedRows: List<ParsedWorkoutRow> = emptyList()
    private var lastBatchId: String? = null

    // ── File selection ────────────────────────────────────────────────────────

    /** Called when the user picks a file via the SAF launcher. */
    fun onFileSelected(uri: Uri) {
        _uiState.update { it.copy(step = ImportStep.Loading) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: run {
                        emitError("Could not read file. Make sure it is a valid CSV.")
                        return@launch
                    }

                val lines = text.lines().filter { it.isNotBlank() }
                if (lines.size < 2) {
                    emitError("No data found. The file appears to be empty or header-only.")
                    return@launch
                }

                val detection = CsvFormatDetector.detect(lines[0])
                cachedLines = lines
                cachedDetection = detection

                val previewFields = lines.drop(1).take(3).map { line ->
                    CsvFormatDetector.splitLine(line, detection.delimiter)
                }

                if (detection.format == CsvFormat.GENERIC) {
                    _uiState.update {
                        it.copy(step = ImportStep.ColumnMapping(detection, previewFields))
                    }
                } else {
                    // Count workouts (approximate: group by date+workoutName key)
                    val parsedRows = CsvRowParser.parseAll(lines, detection)
                    cachedRows = parsedRows
                    val workoutCount = parsedRows
                        .groupBy { "${extractDateKey(it.date)}||${it.workoutName}" }
                        .size
                    _uiState.update {
                        it.copy(step = ImportStep.FormatDetected(detection, workoutCount, previewFields))
                    }
                }
            } catch (e: Exception) {
                emitError("Failed to read file: ${e.message}")
            }
        }
    }

    // ── Flow transitions ──────────────────────────────────────────────────────

    /** Advances from FormatDetected → Options (user confirmed the detected format). */
    fun onConfirmFormat() {
        val current = _uiState.value.step as? ImportStep.FormatDetected ?: return
        _uiState.update {
            it.copy(step = ImportStep.Options(current.detection, cachedRows))
        }
    }

    /** Advances from ColumnMapping → Options with the user-supplied mapping. */
    fun onColumnMappingComplete(mapping: com.powerme.app.data.csvimport.ColumnMapping) {
        val current = _uiState.value.step as? ImportStep.ColumnMapping ?: return
        val detection = current.detection
        viewModelScope.launch(Dispatchers.IO) {
            val parsedRows = CsvRowParser.parseAll(cachedLines, detection, mapping)
            cachedRows = parsedRows
            _uiState.update { it.copy(step = ImportStep.Options(detection, parsedRows)) }
        }
    }

    /** Kicks off the background import with the chosen [options]. */
    fun onStartImport(options: ImportOptions) {
        val rows = cachedRows
        if (rows.isEmpty()) {
            emitError("No rows to import.")
            return
        }

        val initialProgress = ImportProgress(
            totalWorkouts = rows.groupBy { "${extractDateKey(it.date)}||${it.workoutName}" }.size,
            processedWorkouts = 0,
            setsCreated = 0,
            exercisesCreated = 0,
            skippedRows = 0,
            errors = emptyList()
        )
        _uiState.update { it.copy(step = ImportStep.Importing(initialProgress)) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = csvImportManager.import(rows, options) { progress ->
                    _uiState.update { it.copy(step = ImportStep.Importing(progress)) }
                }
                lastBatchId = result.batchId
                _uiState.update { it.copy(step = ImportStep.Complete(result)) }
            } catch (e: Exception) {
                emitError("Import failed: ${e.message}")
            }
        }
    }

    /** Soft-deletes all workouts from the last import batch. */
    fun onUndoImport() {
        val batchId = lastBatchId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            workoutRepository.undoImport(batchId)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(step = ImportStep.Idle) }
            }
        }
    }

    /** Resets state to [ImportStep.Idle]. */
    fun onDismiss() {
        cachedLines = emptyList()
        cachedDetection = null
        cachedRows = emptyList()
        _uiState.update { it.copy(step = ImportStep.Idle) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun emitError(message: String) {
        _uiState.update { it.copy(step = ImportStep.Error(message)) }
    }

    private fun extractDateKey(raw: String): String =
        if (raw.length >= 10) raw.substring(0, 10) else raw
}
