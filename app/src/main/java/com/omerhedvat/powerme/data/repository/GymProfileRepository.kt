package com.omerhedvat.powerme.data.repository

import com.omerhedvat.powerme.data.database.GymProfile
import com.omerhedvat.powerme.data.database.GymProfileDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing gym profiles.
 *
 * Responsibilities:
 * - Provide access to gym profile data
 * - Manage active gym switching
 * - Coordinate with exercise filtering
 */
@Singleton
class GymProfileRepository @Inject constructor(
    private val gymProfileDao: GymProfileDao
) {

    /**
     * Get all gym profiles.
     */
    fun getAllProfiles(): Flow<List<GymProfile>> {
        return gymProfileDao.getAllProfiles()
    }

    /**
     * Get currently active gym profile.
     */
    fun getActiveProfile(): Flow<GymProfile?> {
        return gymProfileDao.getActiveProfile()
    }

    /**
     * Get currently active gym profile (synchronous).
     */
    suspend fun getActiveProfileSync(): GymProfile? {
        return gymProfileDao.getActiveProfileSync()
    }

    /**
     * Get gym profile by ID.
     */
    suspend fun getProfileById(profileId: Long): GymProfile? {
        return gymProfileDao.getProfileById(profileId)
    }

    /**
     * Get gym profile by name.
     */
    suspend fun getProfileByName(name: String): GymProfile? {
        return gymProfileDao.getProfileByName(name)
    }

    /**
     * Insert a new gym profile.
     */
    suspend fun insertProfile(profile: GymProfile): Long {
        return gymProfileDao.insertProfile(profile)
    }

    /**
     * Insert multiple gym profiles.
     */
    suspend fun insertProfiles(profiles: List<GymProfile>) {
        gymProfileDao.insertProfiles(profiles)
    }

    /**
     * Update an existing gym profile.
     */
    suspend fun updateProfile(profile: GymProfile) {
        gymProfileDao.updateProfile(profile)
    }

    /**
     * Delete a gym profile.
     */
    suspend fun deleteProfile(profile: GymProfile) {
        gymProfileDao.deleteProfile(profile)
    }

    /**
     * Switch to a different gym profile.
     *
     * This deactivates all other profiles and activates the selected one.
     *
     * @param profileId ID of the gym profile to activate
     */
    suspend fun setActiveProfile(profileId: Long) {
        gymProfileDao.setActiveProfile(profileId)
    }

    /**
     * Switch to a gym by name.
     *
     * @param gymName Name of the gym to activate
     * @return true if gym was found and activated, false otherwise
     */
    suspend fun setActiveProfileByName(gymName: String): Boolean {
        val profile = gymProfileDao.getProfileByName(gymName)
        return if (profile != null) {
            gymProfileDao.setActiveProfile(profile.id)
            true
        } else {
            false
        }
    }

    /**
     * Get count of gym profiles.
     */
    suspend fun getProfileCount(): Int {
        return gymProfileDao.getProfileCount()
    }

    /**
     * Seed default gym profiles if none exist.
     *
     * Creates:
     * - Home gym (limited equipment)
     * - Work gym (full commercial gym, active by default)
     */
    suspend fun seedDefaultProfilesIfNeeded() {
        val count = getProfileCount()
        if (count == 0) {
            val defaultProfiles = listOf(
                GymProfile(
                    name = "Home",
                    equipment = "Dumbbells,Resistance Bands,Pull-up Bar,Bodyweight",
                    isActive = false,
                    notes = "Home gym with basic equipment"
                ),
                GymProfile(
                    name = "Work",
                    equipment = "Barbell,Dumbbells,Cable,Machine,Bodyweight,Bench,Squat Rack,Leg Press,Smith Machine,Pull-up Bar",
                    isActive = true,
                    notes = "Full commercial gym equipment"
                )
            )
            insertProfiles(defaultProfiles)
        }
    }
}
