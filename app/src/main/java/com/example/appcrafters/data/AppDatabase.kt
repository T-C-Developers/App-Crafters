package com.example.appcrafters.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DirectMessage::class,
        GroupMessage::class,
        User::class,
        Group::class,
        GroupMember::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun directMessageDAO(): DirectMessageDAO
    abstract fun groupMessageDAO(): GroupMessageDAO
    abstract fun groupDAO(): GroupDAO
    abstract fun userDAO(): UserDAO

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "class_connect_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
