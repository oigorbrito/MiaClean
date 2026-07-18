package com.miaclean.app.di

import android.content.Context
import androidx.room.Room
import com.miaclean.app.data.classify.MemeDetector
import com.miaclean.app.data.classify.MediaClassifier
import com.miaclean.app.data.classify.SelfieDetector
import com.miaclean.app.data.classify.MemeSignalsProvider
import com.miaclean.app.data.classify.SelfieSignalsProvider
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.db.MiaCleanDatabase
import com.miaclean.app.data.hash.Md5Hasher
import com.miaclean.shared.dedup.DuplicateOrchestrator
import com.miaclean.shared.hash.ExactHashOrchestrator
import dagger.Binds
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
    fun provideDatabase(@ApplicationContext context: Context): MiaCleanDatabase =
        Room.databaseBuilder(context, MiaCleanDatabase::class.java, "mia-clean.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMediaHashDao(db: MiaCleanDatabase): MediaHashDao = db.mediaHashDao()

    @Provides
    @Singleton
    fun provideMediaClassifier(): MediaClassifier =
        MediaClassifier()

    @Provides
    @Singleton
    fun provideExactHashOrchestrator(md5Hasher: Md5Hasher): ExactHashOrchestrator =
        ExactHashOrchestrator(md5Hasher)



    @Provides
    @Singleton
    fun provideDuplicateOrchestrator(): DuplicateOrchestrator = DuplicateOrchestrator()
}

@Module
@InstallIn(SingletonComponent::class)
object SignalProvidersModule {

    @Provides
    @Singleton
    fun provideSelfieSignalsProvider(impl: SelfieDetector): SelfieSignalsProvider = impl

    @Provides
    @Singleton
    fun provideMemeSignalsProvider(impl: MemeDetector): MemeSignalsProvider = impl
}
