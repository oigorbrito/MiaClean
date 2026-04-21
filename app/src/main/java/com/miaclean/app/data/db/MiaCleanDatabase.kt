package com.miaclean.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MediaHashEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MiaCleanDatabase : RoomDatabase() {
    abstract fun mediaHashDao(): MediaHashDao
}
