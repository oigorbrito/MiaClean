package com.miaclean.app.di

import android.content.Context
import androidx.room.Room
import com.miaclean.app.data.classify.MediaClassifier
import com.miaclean.app.data.hash.AndroidMd5Hasher
import com.miaclean.app.data.hash.AndroidPerceptualHasher
import com.miaclean.app.data.hash.Md5Hasher
import com.miaclean.app.data.hash.PerceptualHasher
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

    @Provides
    @Singleton
    fun provideMediaClassifier(): MediaClassifier = MediaClassifier()

    @Provides
    @Singleton
    fun provideMd5Hasher(hasher: AndroidMd5Hasher): Md5Hasher = hasher

    @Provides
    @Singleton
    fun providePerceptualHasher(hasher: AndroidPerceptualHasher): PerceptualHasher = hasher
}
