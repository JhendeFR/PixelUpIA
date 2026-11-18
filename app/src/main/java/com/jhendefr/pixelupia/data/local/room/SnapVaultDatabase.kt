package com.jhendefr.pixelupia.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import java.util.Locale

@Database(
    entities = [
        SmartPhotoEntity::class,
        SmartPhotoFtsEntity::class,
        FaceClusterEntity::class,
        PhotoClusterCrossRefEntity::class,
        MemoryStoryEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SnapVaultDatabase : RoomDatabase() {
    abstract fun smartGalleryDao(): SmartGalleryDao

    companion object {
        @Volatile
        private var INSTANCE: SnapVaultDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE smart_photos ADD COLUMN manualTags TEXT NOT NULL DEFAULT '[]'")

                val clusterNames = mutableSetOf<String>()
                db.query("SELECT name FROM face_clusters").use { cursor ->
                    while (cursor.moveToNext()) {
                        cursor.getString(0)?.trim()?.lowercase(Locale.ROOT)?.let { clusterNames.add(it) }
                    }
                }

                val aiLabelConfidenceRegex = Regex(""".* - \d+%$""")
                val updates = mutableListOf<Pair<Long, String>>()
                db.query("SELECT photoId, labels FROM smart_photos").use { cursor ->
                    while (cursor.moveToNext()) {
                        val photoId = cursor.getLong(0)
                        val labelsJson = cursor.getString(1) ?: "[]"
                        val parsed = runCatching { JSONArray(labelsJson) }.getOrNull() ?: continue
                        val manualArray = JSONArray()
                        for (i in 0 until parsed.length()) {
                            val raw = parsed.optString(i).trim()
                            if (raw.isBlank()) continue
                            val lower = raw.lowercase(Locale.ROOT)
                            val looksLikeAiConf = aiLabelConfidenceRegex.matches(raw)
                            val looksLikeColor = com.jhendefr.pixelupia.data.ml.ColorAttributeExtractor.isColorLabel(raw)
                            val isCluster = lower in clusterNames
                            if (!looksLikeAiConf && !looksLikeColor && !isCluster) {
                                manualArray.put(raw)
                            }
                        }
                        if (manualArray.length() > 0) {
                            updates.add(photoId to manualArray.toString())
                        }
                    }
                }
                for ((photoId, json) in updates) {
                    db.execSQL(
                        "UPDATE smart_photos SET manualTags = ? WHERE photoId = ?",
                        arrayOf<Any>(json, photoId)
                    )
                }
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE smart_photos ADD COLUMN bannedSearchQueries TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getInstance(context: Context): SnapVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SnapVaultDatabase::class.java,
                    "snapvault_database"
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
