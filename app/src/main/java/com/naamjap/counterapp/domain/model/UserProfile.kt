package com.naamjap.counterapp.domain.model

import java.time.Instant

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val createdAt: Instant
)
