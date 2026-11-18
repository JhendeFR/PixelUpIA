package com.jhendefr.pixelupia.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_stories")
data class MemoryStoryEntity(
    @PrimaryKey(autoGenerate = true)
    val storyId: Long = 0,
    val title: String,
    val subtitle: String,
    val coverUri: String,
    val photoIds: List<Long>,
    val storyType: String, // "PERSON", "PET", "EVENT", "FLASHBACK"
    val isPinned: Boolean = false,
    val isManual: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
