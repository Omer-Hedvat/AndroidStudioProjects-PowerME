package com.powerme.app.di

import com.powerme.app.BuildConfig
import com.powerme.app.data.secure.EncryptedSecurePreferencesStore
import com.powerme.app.data.secure.SecurePreferencesStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurePreferencesModule {

    @Binds
    @Singleton
    abstract fun bindSecurePreferencesStore(
        impl: EncryptedSecurePreferencesStore
    ): SecurePreferencesStore

    companion object {
        @Provides
        @Named("shippedGeminiKey")
        fun provideShippedGeminiKey(): String = BuildConfig.GEMINI_API_KEY
    }
}
