package com.powerme.app.ui.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.BlockType
import com.powerme.app.data.WorkoutStyle
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.Routine
import com.powerme.app.data.database.RoutineBlock
import com.powerme.app.data.database.RoutineBlockDao
import com.powerme.app.data.database.RoutineDao
import com.powerme.app.data.database.RoutineExercise
import com.powerme.app.data.database.RoutineExerciseDao
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.sync.FirestoreSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class DraftExercise(
    val routineExerciseId: Long = 0,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val order: Int,
    val supersetGroupId: String? = null,
    val blockId: String? = null,
    val holdSeconds: Int? = null
)

data class DraftBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType,
    val name: String,
    val durationSeconds: Int? = null,
    val targetRounds: Int? = null,
    val emomRoundSeconds: Int? = null,
    val tabataWorkSeconds: Int? = null,
    val tabataRestSeconds: Int? = null,
    val tabataSkipLastRest: Boolean = false,
    val setupSecondsOverride: Int? = null,
    val warnAtSecondsOverride: Int? = null,
    val order: Int = 0
)

@HiltViewModel
class TemplateBuilderViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao,
    private val routineBlockDao: RoutineBlockDao,
    private val exerciseRepository: ExerciseRepository,
    private val database: PowerMeDatabase,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val appSettingsDataStore: AppSettingsDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val routineId: String = savedStateHandle.get<String>("routineId") ?: "new"

    private val _routineName = MutableStateFlow("")
    val routineName: StateFlow<String> = _routineName.asStateFlow()

    private val _draftExercises = MutableStateFlow<List<DraftExercise>>(emptyList())
    val draftExercises: StateFlow<List<DraftExercise>> = _draftExercises.asStateFlow()

    private val _draftBlocks = MutableStateFlow<List<DraftBlock>>(emptyList())
    val draftBlocks: StateFlow<List<DraftBlock>> = _draftBlocks.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isOrganizeMode = MutableStateFlow(false)
    val isOrganizeMode: StateFlow<Boolean> = _isOrganizeMode.asStateFlow()

    private val _selectedExerciseIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedExerciseIds: StateFlow<Set<Long>> = _selectedExerciseIds.asStateFlow()

    /** Block waiting for exercises from the picker; non-null between wizard close and picker return. */
    private val _pendingBlock = MutableStateFlow<DraftBlock?>(null)
    val pendingBlock: StateFlow<DraftBlock?> = _pendingBlock.asStateFlow()

    val workoutStyle: StateFlow<WorkoutStyle> = appSettingsDataStore.workoutStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutStyle.HYBRID)

    init {
        if (routineId != "new" && routineId.isNotBlank()) {
            viewModelScope.launch {
                val routine = routineDao.getRoutineById(routineId)
                if (routine != null) {
                    _routineName.value = routine.name
                }
                val blocks = routineBlockDao.getBlocksForRoutineOnce(routineId)
                _draftBlocks.value = blocks.mapIndexed { i, b ->
                    DraftBlock(
                        id = b.id,
                        type = BlockType.entries.firstOrNull { it.name == b.type } ?: BlockType.STRENGTH,
                        name = b.name ?: b.type,
                        durationSeconds = b.durationSeconds,
                        targetRounds = b.targetRounds,
                        emomRoundSeconds = b.emomRoundSeconds,
                        tabataWorkSeconds = b.tabataWorkSeconds,
                        tabataRestSeconds = b.tabataRestSeconds,
                        tabataSkipLastRest = (b.tabataSkipLastRest ?: 0) != 0,
                        setupSecondsOverride = b.setupSecondsOverride,
                        warnAtSecondsOverride = b.warnAtSecondsOverride,
                        order = i
                    )
                }
                val exercises = routineExerciseDao.getExercisesWithNamesForRoutine(routineId)
                _draftExercises.value = exercises.mapIndexed { i, ex ->
                    DraftExercise(
                        exerciseId = ex.exerciseId,
                        exerciseName = ex.exerciseName,
                        muscleGroup = ex.muscleGroup,
                        sets = ex.sets,
                        reps = ex.reps,
                        order = i,
                        supersetGroupId = ex.supersetGroupId,
                        blockId = ex.blockId,
                        holdSeconds = ex.holdSeconds
                    )
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _routineName.value = name
    }

    fun addExercises(ids: List<Long>, blockId: String? = null) {
        viewModelScope.launch {
            val existingIds = _draftExercises.value.map { it.exerciseId }.toSet()
            val newIds = ids.filter { it !in existingIds }
            if (newIds.isEmpty()) return@launch
            val blockType = blockId?.let { bid -> _draftBlocks.value.find { it.id == bid }?.type }
            val exercises = exerciseRepository.getExercisesByIds(newIds)
            val currentSize = _draftExercises.value.size
            val newDrafts = exercises.mapIndexed { i, ex ->
                // AMRAP/RFT: TIMED exercises default to time mode (holdSeconds set); others default to reps
                val holdSeconds = if (blockType == BlockType.AMRAP || blockType == BlockType.RFT) {
                    if (ex.exerciseType == ExerciseType.TIMED) ex.restDurationSeconds.coerceAtLeast(10)
                    else null
                } else null
                DraftExercise(
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    muscleGroup = ex.muscleGroup,
                    sets = 3,
                    reps = 10,
                    order = currentSize + i,
                    blockId = blockId,
                    holdSeconds = holdSeconds
                )
            }
            _draftExercises.value = _draftExercises.value + newDrafts
        }
    }

    /** Store the wizard-built block before navigating to the exercise picker. */
    fun setPendingBlock(block: DraftBlock) {
        _pendingBlock.value = block
    }

    /**
     * Called when the exercise picker returns IDs and a pending block is waiting.
     * If the block already exists (Add exercise from overflow menu), only adds exercises.
     * If it's a new block from the wizard, registers the block and adds exercises.
     */
    fun completePendingBlock(exerciseIds: List<Long>) {
        val block = _pendingBlock.value ?: return
        _pendingBlock.value = null
        if (_draftBlocks.value.any { it.id == block.id }) {
            addExercises(exerciseIds, blockId = block.id)
        } else {
            addFunctionalBlock(block, exerciseIds)
        }
    }

    /** Adds a functional block and associates the given exercises with it. */
    fun addFunctionalBlock(block: DraftBlock, exerciseIds: List<Long>) {
        val newOrder = _draftBlocks.value.size
        val orderedBlock = block.copy(order = newOrder)
        _draftBlocks.value = _draftBlocks.value + orderedBlock
        addExercises(exerciseIds, blockId = orderedBlock.id)
    }

    fun deleteBlock(blockId: String) {
        _draftBlocks.value = _draftBlocks.value
            .filter { it.id != blockId }
            .mapIndexed { i, b -> b.copy(order = i) }
        _draftExercises.value = _draftExercises.value
            .filter { it.blockId != blockId }
            .mapIndexed { i, d -> d.copy(order = i) }
    }

    fun reorderBlocks(fromIndex: Int, toIndex: Int) {
        _draftBlocks.value = _draftBlocks.value.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }.mapIndexed { idx, b -> b.copy(order = idx) }
    }

    fun removeExercise(exerciseId: Long) {
        val updated = _draftExercises.value
            .filter { it.exerciseId != exerciseId }
            .mapIndexed { i, d -> d.copy(order = i) }
        // Dissolve any superset that now has only 1 member
        val groupCounts = updated.groupBy { it.supersetGroupId }.filterKeys { it != null }
        val soloGroups = groupCounts.filterValues { it.size == 1 }.keys
        _draftExercises.value = if (soloGroups.isEmpty()) updated
            else updated.map { if (it.supersetGroupId in soloGroups) it.copy(supersetGroupId = null) else it }
        // Remove from selection if present
        _selectedExerciseIds.value = _selectedExerciseIds.value - exerciseId
    }

    fun incrementSets(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId == exerciseId) d.copy(sets = d.sets + 1) else d
        }
    }

    fun decrementSets(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId == exerciseId) d.copy(sets = maxOf(1, d.sets - 1)) else d
        }
    }

    fun incrementReps(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId == exerciseId) d.copy(reps = d.reps + 1) else d
        }
    }

    fun decrementReps(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId == exerciseId) d.copy(reps = maxOf(1, d.reps - 1)) else d
        }
    }

    fun incrementHoldSeconds(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId == exerciseId) d.copy(holdSeconds = (d.holdSeconds ?: 30) + 5) else d
        }
    }

    fun decrementHoldSeconds(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId == exerciseId) d.copy(holdSeconds = maxOf(5, (d.holdSeconds ?: 30) - 5)) else d
        }
    }

    /** Toggles between Reps mode (holdSeconds = null) and Time mode (holdSeconds = 30s default). */
    fun toggleInputMode(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId == exerciseId) {
                if (d.holdSeconds != null) d.copy(holdSeconds = null)
                else d.copy(holdSeconds = 30)
            } else d
        }
    }

    fun reorderDraftExercise(fromIndex: Int, toIndex: Int) {
        _draftExercises.value = _draftExercises.value.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }.mapIndexed { idx, draft -> draft.copy(order = idx) }
    }

    fun enterOrganizeMode() {
        _selectedExerciseIds.value = emptySet()
        _isOrganizeMode.value = true
    }

    fun exitOrganizeMode() {
        _isOrganizeMode.value = false
        _selectedExerciseIds.value = emptySet()
    }

    fun toggleExerciseSelection(exerciseId: Long) {
        val current = _selectedExerciseIds.value
        _selectedExerciseIds.value = if (exerciseId in current) current - exerciseId else current + exerciseId
    }

    fun commitSupersetGroup() {
        val selected = _selectedExerciseIds.value
        if (selected.size < 2) return
        val groupId = UUID.randomUUID().toString()
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId in selected) d.copy(supersetGroupId = groupId) else d
        }
        _selectedExerciseIds.value = emptySet()
        // Stay in organize mode for further grouping
    }

    fun save(onDone: () -> Unit) {
        if (_routineName.value.isBlank()) return
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            _isOrganizeMode.value = false
            _selectedExerciseIds.value = emptySet()
            try {
                val drafts = _draftExercises.value
                val blocks = _draftBlocks.value
                val rid: String = database.withTransaction {
                    if (routineId != "new" && routineId.isNotBlank()) {
                        val existing = routineDao.getRoutineById(routineId)
                        if (existing != null) {
                            routineDao.updateRoutine(
                                existing.copy(name = _routineName.value.trim(), updatedAt = System.currentTimeMillis())
                            )
                        }
                        routineId
                    } else {
                        val newId = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        routineDao.insertRoutine(
                            Routine(id = newId, name = _routineName.value.trim(), isCustom = true, updatedAt = now)
                        )
                        newId
                    }.also { r ->
                        routineExerciseDao.deleteAllForRoutine(r)
                        routineExerciseDao.insertAll(
                            drafts.mapIndexed { i, d ->
                                RoutineExercise(
                                    id = UUID.randomUUID().toString(),
                                    routineId = r,
                                    exerciseId = d.exerciseId,
                                    sets = d.sets,
                                    reps = d.reps,
                                    restTime = 90,
                                    order = i,
                                    supersetGroupId = d.supersetGroupId,
                                    blockId = d.blockId,
                                    holdSeconds = d.holdSeconds
                                )
                            }
                        )
                        routineBlockDao.deleteAllForRoutine(r)
                        if (blocks.isNotEmpty()) {
                            routineBlockDao.upsertAll(
                                blocks.map { b ->
                                    RoutineBlock(
                                        id = b.id,
                                        routineId = r,
                                        order = b.order,
                                        type = b.type.name,
                                        name = b.name,
                                        durationSeconds = b.durationSeconds,
                                        targetRounds = b.targetRounds,
                                        emomRoundSeconds = b.emomRoundSeconds,
                                        tabataWorkSeconds = b.tabataWorkSeconds,
                                        tabataRestSeconds = b.tabataRestSeconds,
                                        tabataSkipLastRest = if (b.tabataSkipLastRest) 1 else 0,
                                        setupSecondsOverride = b.setupSecondsOverride,
                                        warnAtSecondsOverride = b.warnAtSecondsOverride,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                            )
                        }
                    }
                }
                firestoreSyncManager.pushRoutine(rid)
                withContext(Dispatchers.Main) { onDone() }
            } finally {
                _isSaving.value = false
            }
        }
    }
}
