package com.triagemate.chps.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Placeholder for generic application-level providers.
 * EngineProvider is now @Inject-constructable and needs no explicit provision here.
 */
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule
