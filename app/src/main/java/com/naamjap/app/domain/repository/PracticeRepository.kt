package com.naamjap.app.domain.repository

import com.naamjap.app.domain.model.PracticeRecord
import kotlinx.coroutines.flow.Flow

interface PracticeRepository {
    fun observeRecords(): Flow<List<PracticeRecord>>
    suspend fun recordPractice(record: PracticeRecord)
}
