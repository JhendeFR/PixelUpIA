package com.jhendefr.pixelupia.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SmartPhotoEntity::class,
        SmartPhotoFtsEntity::class,
        FaceClusterEntity::class,
        PhotoClusterCrossRefEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SnapVaultDatabase : RoomDatabase() {
    abstract fun smartGalleryDao(): SmartGalleryDao

    companion object {
        @Volatile
        private var INSTANCE: SnapVaultDatabase? = null

        fun getInstance(context: Context): SnapVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SnapVaultDatabase::class.java,
                    "snapvault_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
