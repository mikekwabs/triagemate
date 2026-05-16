package com.triagemate.chps.di

import com.triagemate.chps.data.repository.AssessmentRepositoryImpl
import com.triagemate.chps.data.repository.InferenceRepositoryImpl
import com.triagemate.chps.data.repository.ModelDownloadRepositoryImpl
import com.triagemate.chps.domain.repository.AssessmentRepository
import com.triagemate.chps.domain.repository.InferenceRepository
import com.triagemate.chps.domain.repository.ModelDownloadRepository
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
    abstract fun bindAssessmentRepository(
        impl: AssessmentRepositoryImpl
    ): AssessmentRepository

    @Binds
    @Singleton
    abstract fun bindInferenceRepository(
        impl: InferenceRepositoryImpl
    ): InferenceRepository

    @Binds
    @Singleton
    abstract fun bindModelDownloadRepository(
        impl: ModelDownloadRepositoryImpl
    ): ModelDownloadRepository
}
