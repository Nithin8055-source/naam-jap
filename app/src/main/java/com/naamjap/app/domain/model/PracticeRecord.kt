package com.naamjap.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PracticeRecord(
    val id: String,
    val count: Long,
    val completedAtEpochMillis: Long,
    val note: String? = null
)
