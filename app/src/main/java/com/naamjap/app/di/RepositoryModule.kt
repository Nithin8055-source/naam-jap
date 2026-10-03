package com.naamjap.app.di

import com.naamjap.app.data.remote.auth.SupabaseAuthRepository
import com.naamjap.app.data.remote.profile.SupabaseProfileRepository
import com.naamjap.app.data.remote.practice.SupabasePracticeRepository
import com.naamjap.app.domain.repository.AuthRepository
import com.naamjap.app.domain.repository.ProfileRepository
import com.naamjap.app.domain.repository.PracticeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(implementation: SupabaseProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindPracticeRepository(implementation: SupabasePracticeRepository): PracticeRepository
}
