package com.naamjap.app.domain.repository

import com.naamjap.app.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getCurrentProfile(): UserProfile
    suspend fun updateProfile(displayName: String, avatarUrl: String?)
    suspend fun deleteCurrentAccount()
}
