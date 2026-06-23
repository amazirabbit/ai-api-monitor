package com.yzarc.aiapimonitor.di

import android.content.Context
import android.content.SharedPreferences
import com.yzarc.aiapimonitor.data.db.AppDatabase
import com.yzarc.aiapimonitor.data.repository.BalanceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideBalanceRepository(db: AppDatabase, prefs: SharedPreferences): BalanceRepository {
        return BalanceRepository(db, prefs)
    }
}