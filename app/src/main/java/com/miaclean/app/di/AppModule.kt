package com.miaclean.app.di

import android.content.Context
import androidx.room.Room
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.db.MiaCleanDatabase
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
}
