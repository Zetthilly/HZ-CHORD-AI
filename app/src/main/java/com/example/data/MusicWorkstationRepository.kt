package com.example.data

import kotlinx.coroutines.flow.Flow

class MusicWorkstationRepository(private val workstationDao: WorkstationDao) {

    val allSessions: Flow<List<ProjectSession>> = workstationDao.getAllSessionsFlow()
    val allLicks: Flow<List<GuitarLick>> = workstationDao.getAllLicksFlow()

    fun getLicksByGenre(genre: String): Flow<List<GuitarLick>> = workstationDao.getLicksByGenre(genre)

    suspend fun getSessionById(id: Int): ProjectSession? = workstationDao.getSessionById(id)

    suspend fun insertSession(session: ProjectSession): Long = workstationDao.insertSession(session)

    suspend fun updateSession(session: ProjectSession) = workstationDao.updateSession(session)

    suspend fun deleteSessionById(id: Int) = workstationDao.deleteSessionById(id)

    suspend fun insertLick(lick: GuitarLick): Long = workstationDao.insertLick(lick)

    suspend fun updateLickFavorite(id: Int, isFavorite: Boolean) = workstationDao.updateLickFavorite(id, isFavorite)

    suspend fun deleteLickById(id: Int) = workstationDao.deleteLickById(id)

    // Smart Module State Repository
    val allModuleStates: Flow<List<SmartModuleStateEntity>> = workstationDao.getAllModuleStatesFlow()

    suspend fun getModuleState(moduleId: String): SmartModuleStateEntity? = workstationDao.getModuleState(moduleId)

    suspend fun saveModuleState(state: SmartModuleStateEntity) = workstationDao.saveModuleState(state)

    suspend fun deleteModuleState(moduleId: String) = workstationDao.deleteModuleState(moduleId)

    suspend fun clearAllModuleStates() = workstationDao.clearAllModuleStates()

    suspend fun getGlobalSession(): AppGlobalSessionEntity? = workstationDao.getGlobalSession()

    suspend fun saveGlobalSession(session: AppGlobalSessionEntity) = workstationDao.saveGlobalSession(session)

    // Recording Storage and Reuse System Repository
    val allRecordings: Flow<List<RecordingAssetEntity>> = workstationDao.getAllRecordingsFlow()

    fun searchRecordings(query: String): Flow<List<RecordingAssetEntity>> = workstationDao.searchRecordings(query)

    suspend fun getRecordingById(id: String): RecordingAssetEntity? = workstationDao.getRecordingById(id)

    suspend fun insertRecording(recording: RecordingAssetEntity) = workstationDao.insertRecording(recording)

    suspend fun updateRecording(recording: RecordingAssetEntity) = workstationDao.updateRecording(recording)

    suspend fun deleteRecordingById(id: String) = workstationDao.deleteRecordingById(id)

    suspend fun updateRecordingFavorite(id: String, isFavorite: Boolean) = workstationDao.updateRecordingFavorite(id, isFavorite)
}
