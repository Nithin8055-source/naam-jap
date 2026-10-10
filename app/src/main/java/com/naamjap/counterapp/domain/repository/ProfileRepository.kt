package com.naamjap.counterapp.domain.repository

import com.naamjap.counterapp.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getCurrentProfile(): UserProfile
    suspend fun updateProfile(displayName: String, avatarUrl: String?)
    suspend fun deleteCurrentAccount()
}
