package com.dav3.immichframe.di

import com.dav3.immichframe.data.local.MediaCacheRepositoryImpl
import com.dav3.immichframe.data.local.SettingsRepositoryImpl
import com.dav3.immichframe.data.remote.ImmichRepositoryImpl
import com.dav3.immichframe.data.remote.WeatherRepositoryImpl
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.MediaCacheRepository
import com.dav3.immichframe.domain.repository.SettingsRepository
import com.dav3.immichframe.domain.repository.WeatherRepository
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
    abstract fun bindImmichRepository(impl: ImmichRepositoryImpl): ImmichRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindMediaCacheRepository(impl: MediaCacheRepositoryImpl): MediaCacheRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository
}
