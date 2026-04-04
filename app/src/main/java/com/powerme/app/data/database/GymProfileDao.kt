package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for GymProfile operations.
 *
 * Supports:
 * - CRUD operations for gym profiles
 * - Active gym management (only one active at a time)
 * - Equipment-based queries
 */
@Dao
interface GymProfileDao {

    /**
     * Get all gym profiles.
     */
    @Query("SELECT * FROM gym_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<GymProfile>>

    /**
     * Get gym profile by ID.
     */
    @Query("SELECT * FROM gym_profiles WHERE id = :profileId")
    suspend fun getProfileById(profileId: Long): GymProfile?

    /**
     * Get gym profile by name.
     */
    @Query("SELECT * FROM gym_profiles WHERE name = :name")
    suspend fun getProfileByName(name: String): GymProfile?

    /**
     * Get the currently active gym profile.
     */
    @Query("SELECT * FROM gym_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfile(): Flow<GymProfile?>

    /**
     * Get the currently active gym profile (synchronous for seeding).
     */
    @Query("SELECT * FROM gym_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfileSync(): GymProfile?

    /**
     * Insert a gym profile.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: GymProfile): Long

    /**
     * Insert multiple gym profiles.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<GymProfile>)

    /**
     * Update a gym profile.
     */
    @Update
    suspend fun updateProfile(profile: GymProfile)

    /**
     * Delete a gym profile.
     */
    @Delete
    suspend fun deleteProfile(profile: GymProfile)

    /**
     * Set a gym profile as active and deactivate all others.
     *
     * This is a transaction to ensure only one gym is active at a time.
     */
    @Transaction
    suspend fun setActiveProfile(profileId: Long) {
        // Deactivate all profiles
        deactivateAllProfiles()

        // Activate the selected profile
        activateProfile(profileId)
    }

    /**
     * Deactivate all gym profiles.
     */
    @Query("UPDATE gym_profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()

    /**
     * Activate a specific gym profile.
     */
    @Query("UPDATE gym_profiles SET isActive = 1 WHERE id = :profileId")
    suspend fun activateProfile(profileId: Long)

    /**
     * Get count of gym profiles.
     */
    @Query("SELECT COUNT(*) FROM gym_profiles")
    suspend fun getProfileCount(): Int
}
