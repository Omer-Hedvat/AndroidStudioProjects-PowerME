package com.omerhedvat.powerme.data.repository

import com.omerhedvat.powerme.data.database.GymProfile
import com.omerhedvat.powerme.data.database.GymProfileDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Unit tests for GymProfileRepository.
 *
 * Tests Sprint 5 functionality:
 * - Gym profile CRUD operations
 * - Active gym switching
 * - Equipment filtering
 * - Default profile seeding
 *
 * Success Criteria: All 10 tests must pass.
 */
class GymProfileRepositoryTest {

    private lateinit var repository: GymProfileRepository
    private lateinit var mockDao: GymProfileDao

    private val homeGym = GymProfile(
        id = 1,
        name = "Home",
        equipment = "Dumbbells,Resistance Bands,Pull-up Bar",
        isActive = false
    )

    private val workGym = GymProfile(
        id = 2,
        name = "Work",
        equipment = "Barbell,Dumbbells,Cable,Machine",
        isActive = true
    )

    @Before
    fun setup() {
        mockDao = mock(GymProfileDao::class.java)
        repository = GymProfileRepository(mockDao)
    }

    /**
     * Test Case 1: Get All Profiles
     */
    @Test
    fun testGetAllProfiles() = runTest {
        val profiles = listOf(homeGym, workGym)
        whenever(mockDao.getAllProfiles()).thenReturn(flowOf(profiles))

        val result = repository.getAllProfiles().first()

        assertEquals(2, result.size)
        assertEquals("Home", result[0].name)
        assertEquals("Work", result[1].name)
        verify(mockDao).getAllProfiles()
    }

    /**
     * Test Case 2: Get Active Profile
     */
    @Test
    fun testGetActiveProfile() = runTest {
        whenever(mockDao.getActiveProfile()).thenReturn(flowOf(workGym))

        val result = repository.getActiveProfile().first()

        assertNotNull(result)
        assertEquals("Work", result?.name)
        assertTrue(result!!.isActive)
        verify(mockDao).getActiveProfile()
    }

    /**
     * Test Case 3: Get Profile By Name
     */
    @Test
    fun testGetProfileByName() = runTest {
        whenever(mockDao.getProfileByName("Home")).thenReturn(homeGym)

        val result = repository.getProfileByName("Home")

        assertNotNull(result)
        assertEquals("Home", result?.name)
        assertEquals("Dumbbells,Resistance Bands,Pull-up Bar", result?.equipment)
        verify(mockDao).getProfileByName("Home")
    }

    /**
     * Test Case 4: Get Profile By Name (Not Found)
     */
    @Test
    fun testGetProfileByNameNotFound() = runTest {
        whenever(mockDao.getProfileByName("NonExistent")).thenReturn(null)

        val result = repository.getProfileByName("NonExistent")

        assertNull(result)
        verify(mockDao).getProfileByName("NonExistent")
    }

    /**
     * Test Case 5: Switch Active Profile
     */
    @Test
    fun testSetActiveProfile() = runTest {
        repository.setActiveProfile(1)

        verify(mockDao).setActiveProfile(1)
    }

    /**
     * Test Case 6: Switch Active Profile By Name (Success)
     */
    @Test
    fun testSetActiveProfileByNameSuccess() = runTest {
        whenever(mockDao.getProfileByName("Home")).thenReturn(homeGym)

        val result = repository.setActiveProfileByName("Home")

        assertTrue(result)
        verify(mockDao).getProfileByName("Home")
        verify(mockDao).setActiveProfile(1)
    }

    /**
     * Test Case 7: Switch Active Profile By Name (Not Found)
     */
    @Test
    fun testSetActiveProfileByNameNotFound() = runTest {
        whenever(mockDao.getProfileByName("NonExistent")).thenReturn(null)

        val result = repository.setActiveProfileByName("NonExistent")

        assertFalse(result)
        verify(mockDao).getProfileByName("NonExistent")
        verify(mockDao, never()).setActiveProfile(any())
    }

    /**
     * Test Case 8: Insert Profile
     */
    @Test
    fun testInsertProfile() = runTest {
        val newProfile = GymProfile(
            name = "Travel",
            equipment = "Bodyweight,Resistance Bands",
            isActive = false
        )
        whenever(mockDao.insertProfile(newProfile)).thenReturn(3L)

        val id = repository.insertProfile(newProfile)

        assertEquals(3L, id)
        verify(mockDao).insertProfile(newProfile)
    }

    /**
     * Test Case 9: Update Profile
     */
    @Test
    fun testUpdateProfile() = runTest {
        val updatedHome = homeGym.copy(equipment = "Dumbbells,Kettlebell,Pull-up Bar")

        repository.updateProfile(updatedHome)

        verify(mockDao).updateProfile(updatedHome)
    }

    /**
     * Test Case 10: Seed Default Profiles (Needed)
     */
    @Test
    fun testSeedDefaultProfilesWhenNeeded() = runTest {
        whenever(mockDao.getProfileCount()).thenReturn(0)

        repository.seedDefaultProfilesIfNeeded()

        verify(mockDao).getProfileCount()
        verify(mockDao).insertProfiles(any())
    }

    /**
     * Test Case 11: Seed Default Profiles (Already Exists)
     */
    @Test
    fun testSeedDefaultProfilesAlreadyExists() = runTest {
        whenever(mockDao.getProfileCount()).thenReturn(2)

        repository.seedDefaultProfilesIfNeeded()

        verify(mockDao).getProfileCount()
        verify(mockDao, never()).insertProfiles(any())
    }

    /**
     * Test Case 12: GymProfile Equipment Helper Methods
     */
    @Test
    fun testGymProfileEquipmentHelpers() {
        val gym = GymProfile(
            name = "Test",
            equipment = "Barbell,Dumbbells,Cable,Machine",
            isActive = true
        )

        val equipmentList = gym.getEquipmentList()
        assertEquals(4, equipmentList.size)
        assertTrue(equipmentList.contains("Barbell"))
        assertTrue(equipmentList.contains("Dumbbells"))

        assertTrue(gym.hasEquipment("Barbell"))
        assertTrue(gym.hasEquipment("cable")) // Case insensitive
        assertFalse(gym.hasEquipment("Kettlebell"))
    }
}
