package com.omerhedvat.powerme.data.database

import androidx.room.*

@Dao
interface MedicalLedgerDao {
    @Query("SELECT * FROM medical_ledger ORDER BY lastUpdated DESC LIMIT 1")
    suspend fun getLatestLedger(): MedicalLedger?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedger(ledger: MedicalLedger): Long

    @Query("DELETE FROM medical_ledger")
    suspend fun clearLedger()
}
