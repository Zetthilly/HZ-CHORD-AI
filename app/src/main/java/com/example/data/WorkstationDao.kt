package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkstationDao {
    // Project Session queries
    @Query("SELECT * FROM project_sessions ORDER BY createdAt DESC")
    fun getAllSessionsFlow(): Flow<List<ProjectSession>>

    @Query("SELECT * FROM project_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Int): ProjectSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ProjectSession): Long

    @Update
    suspend fun updateSession(session: ProjectSession)

    @Query("DELETE FROM project_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Int)

    // Guitar Lick queries
    @Query("SELECT * FROM guitar_licks ORDER BY timestamp DESC")
    fun getAllLicksFlow(): Flow<List<GuitarLick>>

    @Query("SELECT * FROM guitar_licks WHERE genre = :genre ORDER BY timestamp DESC")
    fun getLicksByGenre(genre: String): Flow<List<GuitarLick>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLick(lick: GuitarLick): Long

    @Query("UPDATE guitar_licks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateLickFavorite(id: Int, isFavorite: Boolean)

    @Query("DELETE FROM guitar_licks WHERE id = :id")
    suspend fun deleteLickById(id: Int)

    // Smart Module State queries
    @Query("SELECT * FROM smart_module_states WHERE moduleId = :moduleId LIMIT 1")
    suspend fun getModuleState(moduleId: String): SmartModuleStateEntity?

    @Query("SELECT * FROM smart_module_states")
    fun getAllModuleStatesFlow(): Flow<List<SmartModuleStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveModuleState(state: SmartModuleStateEntity)

    @Query("DELETE FROM smart_module_states WHERE moduleId = :moduleId")
    suspend fun deleteModuleState(moduleId: String)

    @Query("DELETE FROM smart_module_states")
    suspend fun clearAllModuleStates()

    // Global App Session
    @Query("SELECT * FROM app_global_session WHERE id = 1 LIMIT 1")
    suspend fun getGlobalSession(): AppGlobalSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGlobalSession(session: AppGlobalSessionEntity)

    // Recording Storage & Reuse System queries
    @Query("SELECT * FROM recording_assets ORDER BY dateCreated DESC")
    fun getAllRecordingsFlow(): Flow<List<RecordingAssetEntity>>

    @Query("SELECT * FROM recording_assets WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: String): RecordingAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingAssetEntity)

    @Update
    suspend fun updateRecording(recording: RecordingAssetEntity)

    @Query("DELETE FROM recording_assets WHERE id = :id")
    suspend fun deleteRecordingById(id: String)

    @Query("UPDATE recording_assets SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateRecordingFavorite(id: String, isFavorite: Boolean)

    @Query("SELECT * FROM recording_assets WHERE recordingName LIKE '%' || :query || '%' OR userNotes LIKE '%' || :query || '%' ORDER BY dateCreated DESC")
    fun searchRecordings(query: String): Flow<List<RecordingAssetEntity>>
}
