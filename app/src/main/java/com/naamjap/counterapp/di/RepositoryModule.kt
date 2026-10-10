package com.naamjap.counterapp.di

import com.naamjap.counterapp.data.remote.auth.SupabaseAuthRepository
import com.naamjap.counterapp.data.remote.profile.SupabaseProfileRepository
import com.naamjap.counterapp.data.remote.practice.SupabasePracticeRepository
import com.naamjap.counterapp.domain.repository.AuthRepository
import com.naamjap.counterapp.domain.repository.ProfileRepository
import com.naamjap.counterapp.domain.repository.PracticeRepository
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
